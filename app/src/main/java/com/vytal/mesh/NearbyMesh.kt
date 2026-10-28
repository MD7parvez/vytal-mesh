package com.vytal.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class NearbyMesh(context:Context,private val unitId:String,private val onStatus:(String)->Unit,private val onMessage:(String)->Unit,private val onPeerCount:(Int)->Unit,private val onPosition:(String,Double,Double,Int,Double)->Unit, private val onBase:(Double,Double)->Unit){
    private val client=Nearby.getConnectionsClient(context.applicationContext)
    private val peers=linkedSetOf<String>();private val serviceId="com.vytal.mesh";private val strategy=Strategy.P2P_CLUSTER;private var running=false
    fun start(){if(running)return;running=true;onStatus("Starting nearby discovery…")
        client.startAdvertising(unitId,serviceId,lifecycle,AdvertisingOptions.Builder().setStrategy(strategy).build()).addOnSuccessListener{onStatus("Advertising as $unitId")}.addOnFailureListener{onStatus("Advertising failed: "+(it.localizedMessage?:"unknown error"))}
        client.startDiscovery(serviceId,discovery,DiscoveryOptions.Builder().setStrategy(strategy).build()).addOnSuccessListener{onStatus("Discovering nearby units…")}.addOnFailureListener{onStatus("Discovery failed: "+(it.localizedMessage?:"unknown error"))}
    }
    fun stop(){running=false;client.stopAdvertising();client.stopDiscovery();client.stopAllEndpoints();peers.clear();onPeerCount(0);onStatus("Discovery stopped")}
    fun sendText(text:String){send(JSONObject().put("msgId",UUID.randomUUID().toString()).put("origin",unitId).put("target","ALL").put("type","TEXT").put("ttl",4).put("timestamp",System.currentTimeMillis()).put("payload",text))}
    fun sendBase(lat:Double,lon:Double){send(JSONObject().put("msgId",UUID.randomUUID().toString()).put("origin",unitId).put("target","ALL").put("type","BASE").put("ttl",4).put("timestamp",System.currentTimeMillis()).put("latitude",lat).put("longitude",lon))}
    fun sendPosition(x:Double,y:Double,floor:Int,uncertainty:Double,baseLat:Double,baseLon:Double){send(JSONObject().put("msgId",UUID.randomUUID().toString()).put("origin",unitId).put("target","ALL").put("type","POSITION").put("ttl",1).put("timestamp",System.currentTimeMillis()).put("x",x).put("y",y).put("floor",floor).put("uncertainty",uncertainty).put("baseLatitude",baseLat).put("baseLongitude",baseLon))}
    private fun send(j:JSONObject){if(peers.isEmpty())return;client.sendPayload(peers.toList(),Payload.fromBytes(j.toString().toByteArray(StandardCharsets.UTF_8))).addOnFailureListener{onStatus("Send failed: "+(it.localizedMessage?:"unknown error"))}}
    private val discovery=object:EndpointDiscoveryCallback(){
        override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){if(info.endpointName==unitId)return;onStatus("Found "+info.endpointName+"; connecting…");client.requestConnection(unitId,id,lifecycle).addOnFailureListener{onStatus("Connection request failed: "+(it.localizedMessage?:"unknown error"))}}
        override fun onEndpointLost(id:String){onStatus("A nearby unit left discovery range")}
    }
    private val lifecycle=object:ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(id:String,info:ConnectionInfo){client.acceptConnection(id,payload).addOnFailureListener{onStatus("Accept failed: "+(it.localizedMessage?:"unknown error"))}}
        override fun onConnectionResult(id:String,result:ConnectionResolution){if(result.status.statusCode==ConnectionsStatusCodes.STATUS_OK){peers.add(id);onPeerCount(peers.size);onStatus("Connected to "+peers.size+" unit(s)")}else onStatus("Connection failed: "+result.status.statusCode)}
        override fun onDisconnected(id:String){peers.remove(id);onPeerCount(peers.size);onStatus("Peer disconnected")}
    }
    private val payload=object:PayloadCallback(){
        override fun onPayloadReceived(id:String,p:Payload){if(p.type!=Payload.Type.BYTES)return;val raw=p.asBytes()?.toString(StandardCharsets.UTF_8)?:return
            try{val j=JSONObject(raw);when(j.optString("type")){
                "TEXT"->onMessage(j.optString("origin","Unknown")+": "+j.optString("payload"))
                "POSITION"->{if(j.optDouble("baseLatitude",Double.NaN).isFinite()&&j.optDouble("baseLongitude",Double.NaN).isFinite())onBase(j.optDouble("baseLatitude"),j.optDouble("baseLongitude"));onPosition(j.optString("origin","Unknown"),j.optDouble("x"),j.optDouble("y"),j.optInt("floor"),j.optDouble("uncertainty",99.0))}
                "BASE"->onBase(j.optDouble("latitude"),j.optDouble("longitude"))
                else->onMessage("Received packet: "+j.optString("type","UNKNOWN"))
            }}catch(_:Exception){onMessage("Received an unreadable packet")}
        }
        override fun onPayloadTransferUpdate(id:String,u:PayloadTransferUpdate){}
    }
}
