package com.vytal.commandcenter

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothService : Service() {
    companion object {
        const val ACTION_CONNECT = "vytal.NODE_CONNECT"
        const val ACTION_DISCONNECT = "vytal.NODE_DISCONNECT"
        const val EXTRA_MAC = "mac"
        const val EXTRA_ROLE = "role"
        const val EVENT = "com.vytal.commandcenter.NODE_EVENT"
        const val EVENT_STATUS = "status"
        const val EVENT_LINE = "line"
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var reader: Thread? = null
    private var running = AtomicBoolean(false)
    private var selectedMac: String? = null
    private var role: String = "FF1"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("vytal_node_bt", "Vytal Node Bluetooth", NotificationManager.IMPORTANCE_LOW))
        role = getSharedPreferences("vytal", MODE_PRIVATE).getString("role", "FF1") ?: "FF1"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 26) startForeground(7, notification())
        if (intent?.action == ACTION_CONNECT) {
            role = intent.getStringExtra(EXTRA_ROLE) ?: role
            selectedMac = intent.getStringExtra(EXTRA_MAC)
            if (selectedMac != null) connect(selectedMac!!)
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "vytal_node_bt") else Notification.Builder(this)
        return builder.setContentTitle("Vytal $role node")
            .setContentText("Maintaining HC-05 sensor connection")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true).build()
    }

    private fun emit(status: String, line: String = "") {
        sendBroadcast(Intent(EVENT).setPackage(packageName)
            .putExtra(EVENT_STATUS, status).putExtra(EVENT_LINE, line))
    }

    @Suppress("MissingPermission")
    private fun connect(mac: String) {
        disconnect()
        running.set(true)
        reader = Thread {
            var delay = 1000L
            while (running.get()) {
                var candidate: BluetoothSocket? = null
                try {
                    emit("CONNECTING")
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: throw IllegalStateException("Bluetooth unavailable")
                    val device: BluetoothDevice = adapter.getRemoteDevice(mac)
                    candidate = device.createRfcommSocketToServiceRecord(SPP)
                    socket = candidate
                    candidate.connect()
                    delay = 1000L
                    emit("CONNECTED")
                    val input = BufferedReader(InputStreamReader(candidate.inputStream, Charsets.UTF_8))
                    while (running.get()) {
                        val line = input.readLine() ?: break
                        emit("DATA", line)
                        sendTelemetry(line)
                    }
                    if (running.get()) emit("LINK LOST")
                } catch (e: Exception) {
                    if (running.get()) emit("RETRY: ${e.message ?: "connection error"}")
                } finally {
                    try { candidate?.close() } catch (_: Exception) {}
                    if (socket === candidate) socket = null
                }
                if (running.get()) {
                    try { Thread.sleep(delay) } catch (_: InterruptedException) {}
                    delay = (delay * 2).coerceAtMost(15000L)
                }
            }
        }.apply { name = "Vytal-HC05-$role"; start() }
    }

    private fun sendTelemetry(line: String) {
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val gateway = wifi.dhcpInfo.gateway
            if (gateway == 0) {
                emit("BT CONNECTED • WI-FI NOT CONNECTED", line)
                return
            }
            val address = InetAddress.getByAddress(byteArrayOf(
                (gateway and 0xff).toByte(), (gateway shr 8 and 0xff).toByte(),
                (gateway shr 16 and 0xff).toByte(), (gateway shr 24 and 0xff).toByte()
            ))
            val message = "VYTAL/1|$role|DATA|$line"
            val bytes = message.toByteArray(Charsets.UTF_8)
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, address, 4210)) }
        } catch (e: Exception) {
            emit("WI-FI SEND ERROR: ${e.message ?: "unknown"}")
        }
    }

    private fun disconnect() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        reader?.interrupt()
        reader = null
        emit("DISCONNECTED")
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
