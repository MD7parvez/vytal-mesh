package com.vytal.commandcenter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import android.graphics.Color
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var lastData: TextView
    private lateinit var log: TextView
    private lateinit var raw: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var alertRow: LinearLayout
    private var role = "FF1"
    @Volatile private var stationRunning = false
    @Volatile private var stationSocket: DatagramSocket? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(BluetoothService.EVENT_STATUS) ?: return
            val line = intent.getStringExtra(BluetoothService.EVENT_LINE) ?: ""
            status.text = "Bluetooth • $state"
            if (line.isNotBlank()) {
                lastData.text = line
                raw.append("$line\n")
                log.text = "Sensor data received from $role"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 15, 28)
        window.navigationBarColor = Color.rgb(8, 15, 28)
        role = getSharedPreferences("vytal", MODE_PRIVATE).getString("role", "FF1") ?: "FF1"
        buildUi()
        val filter = IntentFilter(BluetoothService.EVENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        ensurePermissions()
        applyRole()
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(8, 15, 28))
        }
        fun label(s: String, size: Float = 16f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(Color.WHITE); setPadding(4, 7, 4, 7)
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply {
            text = s; setOnClickListener { action() }
        }
        root.addView(label("VYTAL FIREFIGHTER NODE", 23f))
        root.addView(label("One MCU • One HC-05 • One phone • Wi-Fi uplink", 12f))
        root.addView(label("DEVICE ROLE", 12f))
        val roles = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("FF1", "FF2", "STATION").forEach { value ->
            roles.addView(RadioButton(this).apply {
                text = if (value == "STATION") "Command station" else value
                setTextColor(Color.WHITE)
                isChecked = role == value
                id = View.generateViewId()
                tag = value
            })
        }
        roles.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: "FF1"
            if (selected != role) {
                role = selected
                getSharedPreferences("vytal", MODE_PRIVATE).edit().putString("role", role).apply()
                applyRole()
            }
        }
        root.addView(roles)
        status = label("Bluetooth • Not connected")
        root.addView(status)
        connectButton = button("SELECT PAIRED HC-05 / CONNECT") { chooseDevice() }
        disconnectButton = button("DISCONNECT HC-05") { stopBluetooth() }
        root.addView(connectButton)
        root.addView(disconnectButton)
        root.addView(label("LATEST SENSOR MESSAGE", 12f))
        lastData = label("Waiting for MCU data…", 17f)
        root.addView(lastData)
        alertRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("SOS", "HELP", "GATHER").forEach { command ->
            alertRow.addView(button(command) { sendAlert(command) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(alertRow)
        root.addView(label("NETWORK / DIAGNOSTICS", 12f))
        log = label("Choose FF1 or FF2, connect HC-05, and join the command-station hotspot.", 12f)
        root.addView(log)
        raw = label("", 11f).apply { setTextColor(Color.rgb(110, 225, 190)) }
        val scroll = ScrollView(this)
        scroll.addView(raw)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("Prototype software — not certified life-safety equipment.", 10f))
        setContentView(root)
    }

    private fun applyRole() {
        stopStationReceiver()
        val station = role == "STATION"
        connectButton.visibility = if (station) View.GONE else View.VISIBLE
        disconnectButton.visibility = if (station) View.GONE else View.VISIBLE
        alertRow.visibility = if (station) View.GONE else View.VISIBLE
        if (station) {
            status.text = "Command station • Starting UDP receiver"
            log.text = "Connect this phone to the hotspot network. Keep this screen open to receive FF1 and FF2."
            startStationReceiver()
        } else {
            status.text = "$role • Bluetooth not connected"
            log.text = "$role node • Pair its HC-05 in Android Bluetooth settings, then connect here."
        }
    }

    @Suppress("MissingPermission")
    private fun chooseDevice() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ensurePermissions()
            log.text = "Allow Nearby devices permission, then tap connect again."
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            log.text = "Turn on Bluetooth and pair this node's HC-05 in Android Bluetooth settings."
            return
        }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            log.text = "No paired devices. Pair this phone with its HC-05 in Android Bluetooth settings first."
            return
        }
        val labels = devices.map { "${it.name ?: "Bluetooth device"} • ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select this node's HC-05")
            .setItems(labels) { _, which ->
                val device = devices[which]
                val intent = Intent(this, BluetoothService::class.java)
                    .setAction(BluetoothService.ACTION_CONNECT)
                    .putExtra(BluetoothService.EXTRA_MAC, device.address)
                    .putExtra(BluetoothService.EXTRA_ROLE, role)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                status.text = "Bluetooth • Connecting to ${device.name ?: device.address}"
                log.text = "Persistent Bluetooth SPP connection requested for $role."
            }.setNegativeButton("Cancel", null).show()
    }

    private fun stopBluetooth() {
        startService(Intent(this, BluetoothService::class.java).setAction(BluetoothService.ACTION_DISCONNECT))
    }

    private fun sendAlert(command: String) {
        sendToStation("VYTAL/1|$role|ALERT|$command")
        log.text = "$command alert sent to command-station hotspot, if reachable."
    }

    private fun sendToStation(message: String) {
        thread(name = "VytalUdpSend") {
            try {
                val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val gateway = wifi.dhcpInfo.gateway
                if (gateway == 0) throw IllegalStateException("Not connected to the command-station Wi-Fi hotspot")
                val address = InetAddress.getByAddress(byteArrayOf(
                    (gateway and 0xff).toByte(), (gateway shr 8 and 0xff).toByte(),
                    (gateway shr 16 and 0xff).toByte(), (gateway shr 24 and 0xff).toByte()
                ))
                val bytes = message.toByteArray(Charsets.UTF_8)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(DatagramPacket(bytes, bytes.size, address, 4210))
                }
            } catch (e: Exception) {
                runOnUiThread { log.text = "Wi-Fi uplink: ${e.message ?: "send failed"}" }
            }
        }
    }

    private fun startStationReceiver() {
        if (stationRunning) return
        stationRunning = true
        thread(name = "VytalStationUdp") {
            try {
                val socket = DatagramSocket(4210, InetAddress.getByName("0.0.0.0"))
                stationSocket = socket
                val buffer = ByteArray(4096)
                runOnUiThread { status.text = "Command station • Listening on UDP 4210" }
                while (stationRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    runOnUiThread {
                        raw.append("${packet.address.hostAddress}: $message\n")
                        lastData.text = message
                        log.text = "Received command-station message"
                    }
                }
            } catch (e: Exception) {
                if (stationRunning) runOnUiThread { log.text = "Station receiver error: ${e.message}" }
            } finally {
                stationSocket = null
            }
        }
    }

    private fun stopStationReceiver() {
        stationRunning = false
        stationSocket?.close()
        stationSocket = null
    }

    override fun onDestroy() {
        stopStationReceiver()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
