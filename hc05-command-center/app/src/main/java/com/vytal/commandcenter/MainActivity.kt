package com.vytal.commandcenter

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var status1: TextView
    private lateinit var status2: TextView
    private lateinit var raw: TextView
    private var diagnostics = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val slot = intent?.getStringExtra(BluetoothService.EVENT_SLOT) ?: return
            val status = intent.getStringExtra(BluetoothService.EVENT_STATUS) ?: ""
            val line = intent.getStringExtra(BluetoothService.EVENT_LINE) ?: ""
            val target = if (slot == "FF1") status1 else status2
            target.text = "$slot • $status"
            if (line.isNotBlank()) {
                raw.append("$slot  $line\n")
                log.text = "$slot data received"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8,15,28)
        window.navigationBarColor = Color.rgb(8,15,28)
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        }
        buildUi()
        val filter = IntentFilter(BluetoothService.EVENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18); setBackgroundColor(Color.rgb(8,15,28)) }
        fun text(s: String, size: Float = 16f) = TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(4,8,4,8) }
        fun button(s: String, action: () -> Unit) = Button(this).apply { text=s; setOnClickListener { action() } }
        root.addView(text("VYTAL COMMAND CENTER", 24f))
        root.addView(text("DUAL HC-05 SENSOR LINK • OFFLINE", 12f))
        status1 = text("FF1 • NOT CONNECTED"); status2 = text("FF2 • NOT CONNECTED")
        root.addView(status1); root.addView(button("PAIR / CONNECT FF1") { chooseDevice("FF1") })
        root.addView(status2); root.addView(button("PAIR / CONNECT FF2") { chooseDevice("FF2") })
        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        row.addView(button("SOS") { send("FF1", "SOS"); send("FF2", "SOS") }, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("HELP") { send("FF1", "HELP"); send("FF2", "HELP") }, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("GATHER") { send("FF1", "GATHER"); send("FF2", "GATHER") }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(row)
        root.addView(button("DISCONNECT FF1") { service(BluetoothService.ACTION_DISCONNECT, "FF1") })
        root.addView(button("DISCONNECT FF2") { service(BluetoothService.ACTION_DISCONNECT, "FF2") })
        root.addView(button("DIAGNOSTICS / SERIAL MONITOR") { diagnostics = !diagnostics; raw.visibility = if(diagnostics) android.view.View.VISIBLE else android.view.View.GONE })
        raw = text("Incoming serial data appears here.\n", 12f).apply { visibility=android.view.View.GONE; setTextColor(Color.rgb(100,230,190)) }
        val scroll = ScrollView(this); scroll.addView(raw); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        log = text("Ready • Pair both HC-05 modules in Android Bluetooth settings first.", 12f)
        root.addView(log)
        root.addView(text("Prototype only — not certified life-safety equipment.", 11f))
        setContentView(root)
    }

    @Suppress("MissingPermission")
    private fun chooseDevice(slot: String) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41); return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { log.text="Turn on Bluetooth and pair both HC-05 modules in Settings."; return }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) { log.text="No paired devices. Pair HC-05 modules in Android Bluetooth settings."; return }
        val labels = devices.map { "${it.name ?: "Bluetooth device"} • ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select $slot HC-05")
            .setItems(labels) { _, which ->
                val d=devices[which]
                val i=Intent(this, BluetoothService::class.java).setAction(BluetoothService.ACTION_CONNECT)
                    .putExtra(BluetoothService.EXTRA_SLOT,slot).putExtra(BluetoothService.EXTRA_MAC,d.address)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            }.setNegativeButton("Cancel",null).show()
    }

    private fun service(action: String, slot: String) {
        startService(Intent(this, BluetoothService::class.java).setAction(action).putExtra(BluetoothService.EXTRA_SLOT,slot))
    }
    private fun send(slot: String, message: String) {
        startService(Intent(this, BluetoothService::class.java).setAction(BluetoothService.ACTION_SEND)
            .putExtra(BluetoothService.EXTRA_SLOT,slot).putExtra(BluetoothService.EXTRA_TEXT,message))
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
