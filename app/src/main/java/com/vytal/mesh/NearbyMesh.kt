package com.vytal.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * First milestone transport: Nearby Connections peer discovery and direct text packets.
 * Mesh forwarding, ACK/retry, file payloads and persistent background operation are later milestones.
 */
class NearbyMesh(
    context: Context,
    private val unitId: String,
    private val onStatus: (String) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onPeerCount: (Int) -> Unit
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val peers = linkedSetOf<String>()
    private val serviceId = "com.vytal.mesh"
    private val strategy = Strategy.P2P_CLUSTER
    private var running = false

    fun start() {
        if (running) return
        running = true
        onStatus("Starting offline discovery…")
        client.startAdvertising(
            unitId,
            serviceId,
            lifecycleCallback,
            com.google.android.gms.nearby.connection.AdvertisingOptions.Builder()
                .setStrategy(strategy).build()
        ).addOnSuccessListener { onStatus("Advertising as $unitId") }
            .addOnFailureListener { e -> onStatus("Advertising failed: ${e.localizedMessage ?: "unknown error"}") }

        client.startDiscovery(
            serviceId,
            discoveryCallback,
            com.google.android.gms.nearby.connection.DiscoveryOptions.Builder()
                .setStrategy(strategy).build()
        ).addOnSuccessListener { onStatus("Discovering nearby units…") }
            .addOnFailureListener { e -> onStatus("Discovery failed: ${e.localizedMessage ?: "unknown error"}") }
    }

    fun stop() {
        running = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peers.clear()
        onPeerCount(0)
        onStatus("Offline discovery stopped")
    }

    fun sendText(text: String) {
        if (peers.isEmpty()) {
            onStatus("No connected peers. Keep both devices nearby and start discovery.")
            return
        }
        val packet = JSONObject()
            .put("msgId", UUID.randomUUID().toString())
            .put("origin", unitId)
            .put("target", "ALL")
            .put("type", "TEXT")
            .put("ttl", 4)
            .put("timestamp", System.currentTimeMillis())
            .put("payload", text)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        client.sendPayload(peers.toList(), Payload.fromBytes(packet))
            .addOnFailureListener { e -> onStatus("Send failed: ${e.localizedMessage ?: "unknown error"}") }
        onMessage("Me: $text")
    }

    private val discoveryCallback = object : com.google.android.gms.nearby.connection.EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: com.google.android.gms.nearby.connection.DiscoveredEndpointInfo) {
            if (info.endpointName == unitId) return
            onStatus("Found ${info.endpointName}; requesting connection…")
            client.requestConnection(unitId, endpointId, lifecycleCallback)
                .addOnFailureListener { e -> onStatus("Connection request failed: ${e.localizedMessage ?: "unknown error"}") }
        }

        override fun onEndpointLost(endpointId: String) {
            onStatus("A nearby unit is no longer discoverable")
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Prototype auto-accepts peers using the app's shared service ID. Production deployments
            // should add an explicit trusted-unit enrollment/authentication step.
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e -> onStatus("Accept failed: ${e.localizedMessage ?: "unknown error"}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    peers.add(endpointId)
                    onPeerCount(peers.size)
                    onStatus("Connected to ${peers.size} unit(s)")
                }
                else -> onStatus("Connection failed (code ${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            peers.remove(endpointId)
            onPeerCount(peers.size)
            onStatus("Peer disconnected")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val raw = payload.asBytes()?.toString(StandardCharsets.UTF_8) ?: return
            try {
                val packet = JSONObject(raw)
                if (packet.optString("type") == "TEXT") {
                    onMessage("${packet.optString("origin", "Unknown")}: ${packet.optString("payload")}")
                } else {
                    onMessage("Received packet: ${packet.optString("type", "UNKNOWN")}")
                }
            } catch (_: Exception) {
                onMessage("Received an unreadable packet")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }
}
