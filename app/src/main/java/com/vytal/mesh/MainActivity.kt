package com.vytal.mesh

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private var role = "FIREFIGHTER"
    private var mesh: NearbyMesh? = null
    private lateinit var unitInput: EditText
    private lateinit var statusView: TextView
    private lateinit var peersView: TextView
    private lateinit var logView: TextView
    private lateinit var startButton: Button
    private val requiredPermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 31) {
                permissions += Manifest.permission.BLUETOOTH_SCAN
                permissions += Manifest.permission.BLUETOOTH_CONNECT
                permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            } else {
                permissions += Manifest.permission.ACCESS_FINE_LOCATION
            }
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            return permissions.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.rgb(14, 18, 24))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        content.addView(label("VYTAL MESH", 28, Color.WHITE))
        content.addView(label("OFFLINE FIREFIGHTER TEAM LINK · PROTOTYPE", 12, Color.rgb(255, 90, 75)))
        content.addView(label("Role", 16, Color.LTGRAY))
        val roleRow = LinearLayout(this)
        val fire = button("FIREFIGHTER") { role = "FIREFIGHTER"; appendLog("Role set to FIREFIGHTER") }
        val command = button("COMMANDER") { role = "COMMANDER"; appendLog("Role set to COMMANDER") }
        roleRow.addView(fire, weightParams())
        roleRow.addView(command, weightParams())
        content.addView(roleRow)

        unitInput = EditText(this).apply {
            hint = "Unit ID (e.g. FF-01)"
            setText("FF-01")
            textSize = 18f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(35, 43, 53))
        }
        content.addView(unitInput, marginParams())
        statusView = label("Status: Not connected", 17, Color.rgb(255, 190, 70))
        peersView = label("Connected units: 0", 17, Color.WHITE)
        content.addView(statusView)
        content.addView(peersView)

        startButton = button("START OFFLINE MESH") { startMesh() }
        content.addView(startButton, marginParams())
        content.addView(button("STOP MESH") { mesh?.stop(); mesh = null; startButton.isEnabled = true }, marginParams())

        content.addView(label("QUICK MESSAGES", 18, Color.WHITE))
        listOf("NEED HELP", "ON MY WAY", "OK", "LOW AIR", "NEED SUPPORT", "EVACUATE NOW", "REGROUP AT ENTRANCE").forEach { msg ->
            content.addView(button(msg) { mesh?.sendText(msg) }, marginParams())
        }
        content.addView(label("CUSTOM MESSAGE", 18, Color.WHITE))
        val messageInput = EditText(this).apply {
            hint = "Type a test message"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(35, 43, 53))
        }
        content.addView(messageInput, marginParams())
        content.addView(button("SEND MESSAGE") {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) mesh?.sendText(text)
            messageInput.text.clear()
        }, marginParams())
        content.addView(label("MESSAGE LOG", 18, Color.WHITE))
        logView = label("Prototype ready. Start mesh on two compatible Android phones to test discovery.", 15, Color.LTGRAY)
        content.addView(logView)
        content.addView(label("NOT CERTIFIED FOR LIFE-SAFETY USE. Messages may be lost; do not rely on this prototype during real incidents.", 12, Color.rgb(255, 90, 75)))
    }

    private fun startMesh() {
        if (requiredPermissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(requiredPermissions, 101)
            return
        }
        val id = unitInput.text.toString().trim().ifBlank { "UNIT" }
        mesh?.stop()
        mesh = NearbyMesh(
            this,
            "$role-$id",
            { status -> runOnUiThread { statusView.text = "Status: $status" } },
            { message -> runOnUiThread { appendLog(message) } },
            { count -> runOnUiThread { peersView.text = "Connected units: $count" } }
        )
        mesh?.start()
        startButton.isEnabled = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (requiredPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) startMesh()
            else statusView.text = "Status: Nearby permissions are required for discovery"
        }
    }

    private fun appendLog(message: String) {
        if (!::logView.isInitialized) return
        logView.append("\n$message")
    }

    private fun label(text: String, size: Int, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        setPadding(4, 10, 4, 10)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 16f
        minHeight = 64
        setOnClickListener { action() }
    }

    private fun marginParams() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 6, 0, 6)
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(3, 3, 3, 3)
    }
}
