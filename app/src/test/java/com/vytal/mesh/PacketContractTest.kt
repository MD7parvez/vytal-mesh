package com.vytal.mesh

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketContractTest {
    @Test
    fun packetFieldsCanBeReadBack() {
        val packet = JSONObject()
            .put("msgId", "test-id")
            .put("origin", "FF-01")
            .put("target", "ALL")
            .put("type", "TEXT")
            .put("ttl", 4)
            .put("timestamp", 123L)
            .put("payload", "NEED HELP")
        val decoded = JSONObject(packet.toString())
        assertEquals("test-id", decoded.getString("msgId"))
        assertEquals("FF-01", decoded.getString("origin"))
        assertEquals("NEED HELP", decoded.getString("payload"))
    }
}
