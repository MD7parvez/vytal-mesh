package com.vytal.mesh

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.min

class MainActivity : Activity() {
    private var role = "FIREFIGHTER"
    private var mesh: NearbyMesh? = null
    private var tracker: IndoorTracker? = null
    private lateinit var unitInput: EditText
    private lateinit var statusView: TextView
    private lateinit var peersView: TextView
    private lateinit var positionView: TextView
    private lateinit var logView: TextView
    private lateinit var mapView: MapView
    private var x = 0.0; private var y = 0.0; private var floor = 0
    private var heading = 0.0; private var uncertainty = 2.0
    private var baseLat = Double.NaN; private var baseLon = Double.NaN
    private var gpsManager: LocationManager? = null
    private val gpsSamples = mutableListOf<Location>()
    private var gpsListener: LocationListener? = null
    private val team = linkedMapOf<String, TeamPosition>()
    data class TeamPosition(val x: Double, val y: Double, val floor: Int, val uncertainty: Double)

    private val requiredPermissions: Array<String> get() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { p += Manifest.permission.BLUETOOTH_SCAN; p += Manifest.permission.BLUETOOTH_CONNECT; p += Manifest.permission.BLUETOOTH_ADVERTISE }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 8, 12, 8); setBackgroundColor(Color.rgb(12,17,23)) }
        setContentView(root)
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,0,4,0) }
        scroll.addView(right)
        root.addView(left, LinearLayout.LayoutParams(0,-1,1.35f)); root.addView(scroll, LinearLayout.LayoutParams(0,-1,1f))
        left.addView(label("VYTAL MESH  /  INDOOR TRACKING",22,Color.WHITE))
        left.addView(label("GPS-ANCHORED · SENSOR-ESTIMATED · LANDSCAPE ARM-MOUNT UI",11,Color.rgb(255,184,70)))
        mapView = MapView(); left.addView(mapView, LinearLayout.LayoutParams(-1,0,1f))
        positionView = label("LOCAL X 0.0 m   Y 0.0 m   FLOOR 0\nPosition is estimated, not guaranteed.",14,Color.WHITE); left.addView(positionView)
        right.addView(label("UNIT SETUP",17,Color.WHITE))
        val roleRow = LinearLayout(this)
        roleRow.addView(button("FIREFIGHTER") { role="FIREFIGHTER" }, weightParams())
        roleRow.addView(button("COMMANDER") { role="COMMANDER" }, weightParams()); right.addView(roleRow)
        unitInput = EditText(this).apply { hint="Unit ID"; setText("FF-01"); setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setBackgroundColor(Color.rgb(35,43,53)) }
        right.addView(unitInput, marginParams())
        statusView = label("MESH: STOPPED",14,Color.rgb(255,190,70)); peersView = label("CONNECTED UNITS: 0",14,Color.WHITE)
        right.addView(statusView); right.addView(peersView)
        right.addView(button("START / RESTART MESH") { startMesh() }, marginParams())
        right.addView(button("SET GPS BASE AT ENTRANCE") { requestGpsBase() }, marginParams())
        right.addView(button("MARK ENTRANCE / ZERO POSITION") { if(tracker==null) startTracking(); tracker?.setEntranceReference(); x=0.0; y=0.0; uncertainty=1.0; updatePosition("Entrance origin set. Keep this origin consistent across all units.") }, marginParams())
        right.addView(button("START INDOOR TRACKING") { startTracking() }, marginParams())
        right.addView(button("PAUSE TRACKING") { tracker?.stop(); tracker=null; appendLog("Sensor tracking paused") }, marginParams())
        right.addView(button("BEGIN 20 m STEP CALIBRATION") { tracker?.beginStepCalibration(); appendLog("Walk exactly 20 m, then press FINISH CALIBRATION.") }, marginParams())
        right.addView(button("FINISH STEP CALIBRATION") { val v=tracker?.finishStepCalibration(); if(v!=null) appendLog("Step length saved: %.2f m".format(v)) }, marginParams())
        right.addView(label("QUICK MESSAGES",16,Color.WHITE))
        listOf("NEED HELP","LOW AIR","NEED SUPPORT","EVACUATE NOW","REGROUP AT ENTRANCE","OK").forEach { m -> right.addView(button(m) { mesh?.sendText(m) }, marginParams()) }
        val custom = EditText(this).apply { hint="Custom test message"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setBackgroundColor(Color.rgb(35,43,53)) }
        right.addView(custom,marginParams())
        right.addView(button("SEND MESSAGE") { val m=custom.text.toString().trim(); if(m.isNotEmpty()) mesh?.sendText(m); custom.text.clear() },marginParams())
        right.addView(label("EVENT LOG",16,Color.WHITE))
        logView=label("Ready. Collect GPS outdoors, mark the entrance, then track indoors.",12,Color.LTGRAY); right.addView(logView)
        right.addView(label("PROTOTYPE ONLY — NOT CERTIFIED FOR EMERGENCY USE.",11,Color.rgb(255,90,75)))
    }

    private fun startMesh() {
        if(requiredPermissions.any { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }) { requestPermissions(requiredPermissions,101); return }
        val id=unitInput.text.toString().trim().ifBlank { "UNIT" }
        mesh?.stop()
        mesh=NearbyMesh(this,"$role-$id",
            { s -> runOnUiThread { statusView.text="MESH: $s" } },
            { m -> runOnUiThread { appendLog(m) } },
            { n -> runOnUiThread { peersView.text="CONNECTED UNITS: $n" } },
            { unit,px,py,pf,pu -> runOnUiThread { team[unit]=TeamPosition(px,py,pf,pu); mapView.invalidate(); appendLog("Position received: $unit floor $pf") } },
            { lat,lon -> runOnUiThread { baseLat=lat; baseLon=lon; appendLog("Shared GPS anchor received: %.6f, %.6f".format(lat,lon)); updatePosition("Shared GPS anchor active.") } })
        mesh?.start()
    }

    private fun requestGpsBase() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(requiredPermissions,101); return }
        try {
            gpsManager=getSystemService(LOCATION_SERVICE) as LocationManager; gpsSamples.clear()
            gpsListener?.let { gpsManager?.removeUpdates(it) }
            gpsListener=object: LocationListener {
                override fun onLocationChanged(location: Location) {
                    if(location.hasAccuracy() && location.accuracy<=25f) gpsSamples.add(location)
                    statusView.text="GPS BASE: accurate fixes "+gpsSamples.size+"/5"
                    if(gpsSamples.size>=5) {
                        val a=gpsSamples.take(5); val lat=a.map{it.latitude}.average(); val lon=a.map{it.longitude}.average(); baseLat=lat; baseLon=lon; mesh?.sendBase(lat,lon)
                        gpsManager?.removeUpdates(this)
                        statusView.text="GPS BASE SET · mean accuracy %.1f m".format(a.map{it.accuracy}.average())
                        appendLog("GPS anchor saved: %.6f, %.6f".format(lat,lon))
                    }
                }
                override fun onProviderEnabled(provider:String) {}
                override fun onProviderDisabled(provider:String) { statusView.text="GPS provider disabled" }
            }
            gpsManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,gpsListener!!)
            appendLog("Collecting five accurate GPS fixes. Stay outdoors with clear sky.")
        } catch(e:Exception) { statusView.text="GPS error: "+(e.localizedMessage ?: "check permission") }
    }

    private fun startTracking() {
        if(tracker!=null) return
        tracker=IndoorTracker(this,{ px,py,pf,hdg,unc,steps -> runOnUiThread {
            x=px; y=py; floor=pf; heading=hdg; uncertainty=unc
            updatePosition("Steps $steps · heading %.0f° · uncertainty ±%.1f m".format(hdg,unc))
            mesh?.sendPosition(x,y,floor,unc); mapView.invalidate()
        }},{ e -> runOnUiThread { appendLog(e) } })
        tracker?.start()
    }
    private fun updatePosition(detail:String) { positionView.text="LOCAL X %.1f m   Y %.1f m   FLOOR %d\n%s".format(x,y,floor,detail); mapView.invalidate() }
    private fun appendLog(s:String) { if(::logView.isInitialized) logView.append("\n"+s) }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) { super.onRequestPermissionsResult(r,p,g); if(r==101) { if(requiredPermissions.all{checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}) statusView.text="Permissions granted; start mesh or GPS base." else statusView.text="Location and nearby permissions required." } }
    override fun onDestroy() { tracker?.stop(); gpsListener?.let{gpsManager?.removeUpdates(it)}; mesh?.stop(); super.onDestroy() }

    private fun label(t:String,size:Int,c:Int)=TextView(this).apply{ text=t;textSize=size.toFloat();setTextColor(c);setPadding(4,4,4,4) }
    private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=12f;minHeight=44;setOnClickListener{a()}}
    private fun marginParams()=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(0,2,0,2)}
    private fun weightParams()=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{setMargins(2,2,2,2)}

    private inner class MapView:View(this@MainActivity) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas) {
            super.onDraw(c); c.drawColor(Color.rgb(18,25,33)); val w=width.toFloat(); val h=height.toFloat()
            p.color=Color.rgb(42,54,66);p.strokeWidth=1f
            var gx=0f;while(gx<w){c.drawLine(gx,0f,gx,h,p);gx+=40f};var gy=0f;while(gy<h){c.drawLine(0f,gy,w,gy,p);gy+=40f}
            p.color=Color.LTGRAY;p.textSize=12f;c.drawText("FLOOR $floor · LOCAL METRES · ESTIMATED TRACK",10f,18f,p)
            val scale=min(w,h)/24f;val cx=w/2f;val cy=h/2f
            p.color=Color.rgb(80,200,130);p.style=Paint.Style.FILL;c.drawCircle(cx,cy,7f,p);p.color=Color.WHITE;c.drawText("YOU",cx+10f,cy-8f,p)
            team.forEach{(name,q)->if(q.floor==floor){val tx=cx+((q.x-x)*scale).toFloat();val ty=cy-((q.y-y)*scale).toFloat();p.color=Color.rgb(255,185,65);c.drawCircle(tx,ty,6f,p);p.color=Color.WHITE;c.drawText(name.take(10),tx+8f,ty-5f,p)}}
            p.color=Color.rgb(255,185,65);c.drawText("UNCERTAINTY ±%.1f m".format(uncertainty),10f,h-10f,p)
        }
    }
}
