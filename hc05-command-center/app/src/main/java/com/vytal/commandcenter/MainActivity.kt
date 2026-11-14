package com.vytal.commandcenter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var heartRate: TextView
    private lateinit var temperature: TextView
    private lateinit var humidity: TextView
    private lateinit var lastData: TextView
    private lateinit var log: TextView
    private lateinit var raw: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var alertRow: LinearLayout
    private var role = "FF1"
    @Volatile private var stationRunning = false
    @Volatile private var stationSocket: DatagramSocket? = null
    @Volatile private var nodeRunning = false
    @Volatile private var nodeSocket: DatagramSocket? = null
    private val bg = Color.rgb(7, 13, 27)
    private val panel = Color.rgb(17, 27, 48)
    private val muted = Color.rgb(151, 168, 194)
    private val cyan = Color.rgb(48, 205, 220)
    private val green = Color.rgb(64, 210, 145)
    private val amber = Color.rgb(255, 184, 77)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(BluetoothService.EVENT_STATUS) ?: return
            val line = intent.getStringExtra(BluetoothService.EVENT_LINE) ?: ""
            status.text = "$role  •  $state"
            status.setTextColor(if (state.contains("CONNECTED")) green else amber)
            if (line.isNotBlank()) {
                lastData.text = line
                parseSensorLine(line, role)
                raw.append("$line\n")
                log.text = "Live sensor stream from $role"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = bg
        window.navigationBarColor = bg
        role = getSharedPreferences("vytal", MODE_PRIVATE).getString("role", "FF1") ?: "FF1"
        buildUi()
        val filter = IntentFilter(BluetoothService.EVENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        ensurePermissions()
        applyRole()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun styledButton(value: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            text = value; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(color, 12); setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
        }

    private fun metricCard(caption: String, accent: Int): Pair<LinearLayout, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel)
            setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        card.addView(text(caption.uppercase(), 10f, muted, true))
        val value = text("—", 27f, accent, true)
        card.addView(value)
        return Pair(card, value)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(bg)
        }
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel, 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        sidebar.layoutParams = LinearLayout.LayoutParams(dp(230), -1).apply { marginEnd = dp(12) }
        sidebar.addView(text("VYTAL", 25f, cyan, true))
        sidebar.addView(text("FIREFIGHTER SYSTEM", 10f, muted, true))
        sidebar.addView(View(this).apply { setBackgroundColor(Color.rgb(43, 59, 86)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(8) })
        sidebar.addView(text("DEVICE ROLE", 10f, muted, true))
        val roles = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("FF1", "FF2", "STATION").forEach { value ->
            roles.addView(RadioButton(this).apply {
                text = value; textSize = 11f; setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
                isChecked = role == value; id = View.generateViewId(); tag = value
            })
        }
        roles.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: "FF1"
            if (selected != role) {
                stopBluetooth(); role = selected
                getSharedPreferences("vytal", MODE_PRIVATE).edit().putString("role", role).apply()
                applyRole()
            }
        }
        sidebar.addView(roles)
        status = text("●  Not connected", 12f, amber, true)
        status.setPadding(0, dp(8), 0, dp(8)); sidebar.addView(status)
        connectButton = styledButton("CONNECT HC-05", Color.rgb(31, 119, 142)) { chooseDevice() }
        disconnectButton = styledButton("DISCONNECT", Color.rgb(65, 78, 101)) { stopBluetooth() }
        sidebar.addView(connectButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) })
        sidebar.addView(disconnectButton, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(6) })
        sidebar.addView(styledButton("GITHUB REPOSITORY", Color.rgb(55, 75, 112)) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MD7parvez/vytal-mesh")))
        }, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(6) })
        sidebar.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        sidebar.addView(text("NETWORK  •  UDP 4210", 10f, muted, true))
        sidebar.addView(text("Prototype only — not certified life-safety equipment.", 9f, muted))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, 0, 0) }
        content.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headerTitle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headerTitle.addView(text("LIVE VITALS", 20f, Color.WHITE, true))
        headerTitle.addView(text("Bluetooth sensor link  /  Wi-Fi command-station uplink", 10f, muted))
        header.addView(headerTitle, LinearLayout.LayoutParams(0, -2, 1f))
        lastData = text("Waiting for $role data…", 10f, muted)
        header.addView(lastData)
        content.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hr = metricCard("Heart rate  •  BPM", green); heartRate = hr.second
        val temp = metricCard("Temperature  •  °C", amber); temperature = temp.second
        val humid = metricCard("Humidity  •  %", cyan); humidity = humid.second
        listOf(hr.first, temp.first, humid.first).forEachIndexed { i, card ->
            metrics.addView(card, LinearLayout.LayoutParams(0, dp(88), 1f).apply { if (i > 0) marginStart = dp(9) })
        }
        content.addView(metrics)

        val lower = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        actions.addView(text("FIREFIGHTER ALERTS", 10f, muted, true))
        alertRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        alertRow.addView(styledButton("SOS", Color.rgb(176, 45, 61)) { sendAlert("SOS") },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5); topMargin = dp(5) })
        alertRow.addView(styledButton("HELP", Color.rgb(169, 105, 35)) { sendAlert("HELP") },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5); topMargin = dp(5) })
        alertRow.addView(styledButton("GATHER", Color.rgb(45, 91, 166)) { sendAlert("GATHER") },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { topMargin = dp(5) })
        actions.addView(alertRow)
        lower.addView(actions, LinearLayout.LayoutParams(0, -2, 1.05f).apply { marginEnd = dp(9) })

        val diagnostics = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        diagnostics.addView(text("EVENTS  /  DIAGNOSTICS", 10f, muted, true))
        log = text("Choose a role to begin.", 11f, Color.WHITE); diagnostics.addView(log)
        raw = text("", 9f, green)
        val scroll = ScrollView(this); scroll.addView(raw)
        diagnostics.addView(scroll, LinearLayout.LayoutParams(-1, dp(64)))
        lower.addView(diagnostics, LinearLayout.LayoutParams(0, -1, 1f))
        content.addView(lower, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        root.addView(sidebar); root.addView(content); setContentView(root)
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
    }

    private fun parseSensorLine(line: String, expectedRole: String) {
        val p = line.trim().split("|")
        if (p.size >= 5 && p[0] == "DATA") {
            if (p[1] != expectedRole) return
            val bpm = p[2].toIntOrNull()
            val temp = p[3].toFloatOrNull()
            val rh = p[4].toFloatOrNull()
            heartRate.text = if (bpm != null && bpm in 1..240) "$bpm" else "—"
            temperature.text = if (temp != null && temp in -20f..100f) "%.1f".format(temp) else "—"
            humidity.text = if (rh != null && rh in 0f..100f) "%.0f".format(rh) else "—"
            log.text = "$expectedRole • sensor frame received"
        } else if (p.size >= 3 && p[0] == "BUTTON") {
            log.text = "ALERT  •  " + p[1] + "  •  " + p[2]
        }
    }

    private fun applyRole() {
        stopStationReceiver()
        stopNodeReceiver()
        val station = role == "STATION"
        connectButton.visibility = if (station) View.GONE else View.VISIBLE
        disconnectButton.visibility = if (station) View.GONE else View.VISIBLE
        alertRow.visibility = if (station) View.GONE else View.VISIBLE
        if (station) {
            status.text = "●  STATION • STARTING"; status.setTextColor(cyan)
            log.text = "Listening for FF1 and FF2 over the hotspot."
            lastData.text = "Command station receiver"; startStationReceiver()
        } else {
            status.text = "●  $role • BLUETOOTH OFFLINE"; status.setTextColor(amber)
            log.text = "$role • Pair this phone with its own HC-05 in Android Bluetooth settings."
            lastData.text = "Waiting for $role data…"
            startNodeReceiver()
        }
    }

    @Suppress("MissingPermission")
    private fun chooseDevice() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ensurePermissions(); log.text = "Allow Nearby devices permission, then tap CONNECT HC-05 again."; return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { log.text = "Enable Bluetooth and pair this phone with its HC-05 first."; return }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) { log.text = "No paired devices. Pair the HC-05 in Android Bluetooth settings first."; return }
        val labels = devices.map { "${it.name ?: "Bluetooth device"} • ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select this node's HC-05").setItems(labels) { _, which ->
            val device = devices[which]
            val intent = Intent(this, BluetoothService::class.java).setAction(BluetoothService.ACTION_CONNECT)
                .putExtra(BluetoothService.EXTRA_MAC, device.address).putExtra(BluetoothService.EXTRA_ROLE, role)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "●  $role • CONNECTING"; status.setTextColor(amber)
            log.text = "Persistent Bluetooth SPP connection requested."
        }.setNegativeButton("Cancel", null).show()
    }

    private fun stopBluetooth() {
        startService(Intent(this, BluetoothService::class.java).setAction(BluetoothService.ACTION_DISCONNECT))
    }

    private fun sendAlert(command: String) {
        sendToStation("VYTAL/1|$role|ALERT|$command")
        log.text = "$command sent to command station, if reachable."
    }

    private fun sendToStation(message: String) {
        thread(name = "VytalUdpSend") {
            try {
                val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val gateway = wifi.dhcpInfo.gateway
                if (gateway == 0) throw IllegalStateException("Not connected to command-station hotspot")
                val address = InetAddress.getByAddress(byteArrayOf(
                    (gateway and 0xff).toByte(), (gateway shr 8 and 0xff).toByte(),
                    (gateway shr 16 and 0xff).toByte(), (gateway shr 24 and 0xff).toByte()))
                val bytes = message.toByteArray(Charsets.UTF_8)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(DatagramPacket(bytes, bytes.size, address, 4210))
                }
            } catch (e: Exception) {
                runOnUiThread { log.text = "Wi-Fi uplink: " + (e.message ?: "send failed") }
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
                runOnUiThread { status.text = "●  STATION • LISTENING"; status.setTextColor(green) }
                while (stationRunning) {
                    val packet = DatagramPacket(buffer, buffer.size); socket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    runOnUiThread {
                        raw.append(packet.address.hostAddress + ": " + message + "\n")
                        val fields = message.split("|", limit = 5)
                        if (fields.size >= 4 && fields[0] == "VYTAL/1") {
                            forwardToNodes(message)
                            if (fields.size >= 5 && fields[2] == "DATA") {
                                lastData.text = "Latest: " + fields[1]
                                parseSensorLine(fields.drop(2).joinToString("|"), fields[1])
                            } else {
                                log.text = "RELAY  •  " + fields[1] + "  •  " + fields.drop(2).joinToString(" ")
                            }
                        } else lastData.text = "Received packet"
                    }
                }
            } catch (e: Exception) {
                if (stationRunning) runOnUiThread { log.text = "Station receiver: " + (e.message ?: "error") }
            } finally { stationSocket = null }
        }
    }

    private fun forwardToNodes(message: String) {
        thread(name = "VytalMeshForward") {
            try {
                val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wifi.dhcpInfo
                val ip = info.ipAddress
                val mask = if (info.netmask != 0) info.netmask else 0x00ffffff
                val broadcast = (ip and mask.inv()) or mask
                val address = InetAddress.getByAddress(byteArrayOf(
                    (broadcast and 0xff).toByte(), (broadcast shr 8 and 0xff).toByte(),
                    (broadcast shr 16 and 0xff).toByte(), (broadcast shr 24 and 0xff).toByte()))
                val payload = message.toByteArray(Charsets.UTF_8)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(DatagramPacket(payload, payload.size, address, 4211))
                }
            } catch (e: Exception) {
                runOnUiThread { log.text = "Mesh relay error: " + (e.message ?: "send failed") }
            }
        }
    }

    private fun startNodeReceiver() {
        if (nodeRunning) return
        nodeRunning = true
        thread(name = "VytalNodeRelayRx") {
            try {
                val socket = DatagramSocket(4211, InetAddress.getByName("0.0.0.0"))
                nodeSocket = socket
                val buffer = ByteArray(4096)
                while (nodeRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    if (!message.startsWith("VYTAL/1|$role|")) {
                        runOnUiThread {
                            raw.append("RELAY FROM " + packet.address.hostAddress + ": " + message + "\\n")
                            log.text = "MESH RELAY  •  " + message.take(90)
                        }
                    }
                }
            } catch (e: Exception) {
                if (nodeRunning) runOnUiThread { log.text = "Node relay: " + (e.message ?: "receiver error") }
            } finally { nodeSocket = null }
        }
    }

    private fun stopNodeReceiver() {
        nodeRunning = false
        nodeSocket?.close()
        nodeSocket = null
    }

    private fun stopStationReceiver() {
        stationRunning = false; stationSocket?.close(); stationSocket = null
    }

    override fun onDestroy() {
        stopStationReceiver()
        stopNodeReceiver()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
