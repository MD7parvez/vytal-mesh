package com.vytal.commandcenter

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "Vytal")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        eventSink = { payload -> runOnUiThread {
            if (::web.isInitialized) web.evaluateJavascript("window.onNativeEvent && window.onNativeEvent($payload)", null)
        } }
    }

    override fun onPause() {
        eventSink = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val ok = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
        web.evaluateJavascript("window.onPermissionResult && window.onPermissionResult($ok)", null)
    }

    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    inner class Bridge {
        @JavascriptInterface fun requestPermissions() {
            if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission()) {
                this@MainActivity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 41)
            } else {
                runOnUiThread { web.evaluateJavascript("window.onPermissionResult && window.onPermissionResult(true)", null) }
            }
        }

        @JavascriptInterface fun bondedDevices(): String {
            if (!hasBtPermission()) return JSONArray().toString()
            return try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val arr = JSONArray()
                adapter?.bondedDevices?.forEach { d ->
                    arr.put(JSONObject().put("name", d.name ?: "Bluetooth device").put("address", d.address))
                }
                arr.toString()
            } catch (_: SecurityException) { JSONArray().toString() }
        }

        @JavascriptInterface fun connect(node: String, address: String) {
            if (!hasBtPermission()) { requestPermissions(); return }
            val i = Intent(this@MainActivity, BluetoothService::class.java)
                .setAction(BluetoothService.ACTION_CONNECT)
                .putExtra("node", node).putExtra("address", address)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }

        @JavascriptInterface fun disconnect(node: String) {
            startService(Intent(this@MainActivity, BluetoothService::class.java)
                .setAction(BluetoothService.ACTION_DISCONNECT).putExtra("node", node))
        }

        @JavascriptInterface fun send(node: String, message: String) {
            BluetoothService.send(node, message)
        }

        @JavascriptInterface fun appVersion() = "1.0"
    }

    companion object {
        @Volatile var eventSink: ((String) -> Unit)? = null
        fun emit(json: JSONObject) { eventSink?.invoke(json.toString()) }
    }
}
