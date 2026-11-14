package com.vytal.commandcenter

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothService : Service() {
    companion object {
        const val ACTION_CONNECT = "vytal.CONNECT"
        const val ACTION_DISCONNECT = "vytal.DISCONNECT"
        const val ACTION_SEND = "vytal.SEND"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_MAC = "mac"
        const val EXTRA_TEXT = "text"
        const val EVENT = "com.vytal.commandcenter.EVENT"
        const val EVENT_SLOT = "slot"
        const val EVENT_STATUS = "status"
        const val EVENT_LINE = "line"
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private data class Link(val slot: String, val mac: String, var socket: BluetoothSocket? = null,
                            var reader: Thread? = null, val running: AtomicBoolean = AtomicBoolean(true))
    private val links = mutableMapOf<String, Link>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("vytal_bt", "Vytal Bluetooth", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(7, notification())
        when (intent?.action) {
            ACTION_CONNECT -> connect(intent.getStringExtra(EXTRA_SLOT) ?: "FF1", intent.getStringExtra(EXTRA_MAC) ?: return START_STICKY)
            ACTION_DISCONNECT -> disconnect(intent.getStringExtra(EXTRA_SLOT) ?: "FF1")
            ACTION_SEND -> send(intent.getStringExtra(EXTRA_SLOT) ?: "FF1", intent.getStringExtra(EXTRA_TEXT) ?: "")
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "vytal_bt") else Notification.Builder(this)
        return b.setContentTitle("Vytal Command Center")
            .setContentText("Bluetooth monitoring service active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true).build()
    }

    private fun emit(slot: String, status: String, line: String = "") {
        sendBroadcast(Intent(EVENT).setPackage(packageName).putExtra(EVENT_SLOT, slot)
            .putExtra(EVENT_STATUS, status).putExtra(EVENT_LINE, line))
    }

    @Suppress("MissingPermission")
    private fun connect(slot: String, mac: String) {
        disconnect(slot)
        val link = Link(slot, mac)
        links[slot] = link
        link.reader = Thread {
            var delay = 1000L
            while (link.running.get()) {
                var socket: BluetoothSocket? = null
                try {
                    emit(slot, "CONNECTING")
                    val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth unavailable")
                    val device: BluetoothDevice = adapter.getRemoteDevice(mac)
                    socket = device.createRfcommSocketToServiceRecord(SPP)
                    link.socket = socket
                    socket.connect()
                    delay = 1000L
                    emit(slot, "CONNECTED")
                    val input = BufferedReader(InputStreamReader(socket.inputStream))
                    while (link.running.get()) {
                        val line = input.readLine() ?: break
                        emit(slot, "DATA", line)
                    }
                    if (link.running.get()) emit(slot, "LINK LOST")
                } catch (e: Exception) {
                    if (link.running.get()) emit(slot, "RETRY: " + (e.message ?: "connection error"))
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    if (link.socket === socket) link.socket = null
                }
                if (link.running.get()) {
                    try { Thread.sleep(delay) } catch (_: InterruptedException) {}
                    delay = (delay * 2).coerceAtMost(15000L)
                }
            }
        }.apply { name = "Vytal-$slot"; start() }
    }

    private fun send(slot: String, text: String) {
        val link = links[slot]
        Thread {
            try {
                val s = link?.socket ?: throw IllegalStateException("Not connected")
                synchronized(s) { s.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8)); s.outputStream.flush() }
                emit(slot, "SENT", text)
            } catch (e: Exception) { emit(slot, "SEND ERROR", e.message ?: "write failed") }
        }.start()
    }

    private fun disconnect(slot: String) {
        val l = links.remove(slot) ?: return
        l.running.set(false)
        try { l.socket?.close() } catch (_: Exception) {}
        l.reader?.interrupt()
        emit(slot, "DISCONNECTED")
    }

    override fun onDestroy() {
        links.keys.toList().forEach { disconnect(it) }
        super.onDestroy()
    }
}
