package com.vytal.commandcenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

class BluetoothService : Service() {
    private val workers = Executors.newCachedThreadPool()
    private val retry: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val sockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val addresses = ConcurrentHashMap<String, String>()
    private val attempts = ConcurrentHashMap<String, Int>()
    private val connecting = ConcurrentHashMap<String, Boolean>()
    @Volatile private var stopped = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("vytal_bt", "Vytal Bluetooth", NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "vytal_bt").setContentTitle("Vytal is monitoring connections")
                .setContentText("Bluetooth sensor links remain active while the service runs")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("Vytal is running")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
        }
        startForeground(71, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val node = intent.getStringExtra("node") ?: return START_STICKY
                val address = intent.getStringExtra("address") ?: return START_STICKY
                addresses[node] = address
                attempts[node] = 0
                connectNow(node, address)
            }
            ACTION_DISCONNECT -> {
                val node = intent.getStringExtra("node") ?: return START_STICKY
                addresses.remove(node)
                sockets.remove(node)?.let { try { it.close() } catch (_: Exception) {} }
                connecting.remove(node)
                emitStatus(node, "disconnected", "Disconnected by user")
                if (addresses.isEmpty()) stopSelf()
            }
        }
        return START_STICKY
    }

    private fun connectNow(node: String, address: String) {
        if (stopped || connecting.putIfAbsent(node, true) == true) return
        workers.execute {
            var socket: BluetoothSocket? = null
            try {
                emitStatus(node, "connecting", "Opening Bluetooth SPP socket")
                val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                sockets[node] = socket
                attempts[node] = 0
                emitStatus(node, "connected", "Serial link established")
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                while (!stopped && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) MainActivity.emit(JSONObject()
                        .put("type", "data").put("node", node).put("line", line)
                        .put("time", System.currentTimeMillis()))
                }
                throw IllegalStateException("Remote device closed the stream")
            } catch (e: Exception) {
                emitStatus(node, "disconnected", e.message ?: "Bluetooth connection lost")
            } finally {
                connecting.remove(node)
                sockets.remove(node)
                try { socket?.close() } catch (_: Exception) {}
                if (!stopped && addresses[node] == address) scheduleRetry(node, address)
            }
        }
    }

    private fun scheduleRetry(node: String, address: String) {
        val n = (attempts[node] ?: 0) + 1
        attempts[node] = n
        val delay = min(30L, 2L shl min(n - 1, 4))
        emitStatus(node, "retrying", "Retry in ${delay}s (attempt $n)")
        retry.schedule({ if (!stopped && addresses[node] == address) connectNow(node, address) }, delay, TimeUnit.SECONDS)
    }

    private fun emitStatus(node: String, status: String, detail: String) {
        MainActivity.emit(JSONObject().put("type", "status").put("node", node)
            .put("status", status).put("detail", detail).put("time", System.currentTimeMillis()))
    }

    override fun onDestroy() {
        stopped = true
        sockets.values.forEach { try { it.close() } catch (_: Exception) {} }
        sockets.clear()
        workers.shutdownNow()
        retry.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CONNECT = "com.vytal.commandcenter.CONNECT"
        const val ACTION_DISCONNECT = "com.vytal.commandcenter.DISCONNECT"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        fun send(node: String, message: String) {
            // Output is deliberately unavailable until a node command protocol is defined.
            MainActivity.emit(JSONObject().put("type", "log").put("node", node)
                .put("line", "TX not sent: define the node command protocol first"))
        }
    }
}
