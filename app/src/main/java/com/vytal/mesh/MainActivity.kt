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
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.max

class MainActivity : Activity() {
    private var role = "FIREFIGHTER"
    private var mesh: NearbyMesh? = null
    private var tracker: IndoorTracker? = null
    private lateinit var unitInput: EditText
    private lateinit var statusView: TextView
    private lateinit var peersView: TextView
    private lateinit var positionView: TextView
    private lateinit var logView: TextView
    private lateinit var root: LinearLayout
    private lateinit var dashboard: LinearLayout
    private lateinit var mapScreen: LinearLayout
    private lateinit var mapView: Map3DView
    private var x=0.0; private var y=0.0; private var floor=0
    private var heading=0.0; private var uncertainty=2.0
    private var baseLat=Double.NaN; private var baseLon=Double.NaN
    private var gpsManager:LocationManager?=null
    private val gpsSamples=mutableListOf<Location>()
    private var gpsListener:LocationListener?=null
    private val team=linkedMapOf<String,TeamPosition>()
    private val trail=mutableListOf<Point3>()
    data class TeamPosition(val x:Double,val y:Double,val floor:Int,val uncertainty:Double)
    data class Point3(val x:Double,val y:Double,val z:Double)

    private val requiredPermissions:Array<String> get(){
        val p=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=31){p+=Manifest.permission.BLUETOOTH_SCAN;p+=Manifest.permission.BLUETOOTH_CONNECT;p+=Manifest.permission.BLUETOOTH_ADVERTISE}
        if(Build.VERSION.SDK_INT>=33)p+=Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;window.statusBarColor=Color.rgb(10,16,32);window.navigationBarColor=Color.rgb(10,16,32);buildUi()}

    private fun buildUi(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(8,13,28));setPadding(8,6,8,6)}
        setContentView(root)
        dashboard=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
        val title=label("VYTAL MESH",24,Color.WHITE);title.setTypeface(null,android.graphics.Typeface.BOLD)
        header.addView(title,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        header.addView(label("FIELD TEAM • SENSOR NAV",12,Color.rgb(0,229,255)))
        dashboard.addView(header)
        val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        dashboard.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val left=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(4,2,6,2)}
        val mid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(4,2,6,2)}
        val right=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(4,2,2,2)}
        body.addView(left,LinearLayout.LayoutParams(0,-1,1f));body.addView(mid,LinearLayout.LayoutParams(0,-1,1f));body.addView(right,LinearLayout.LayoutParams(0,-1,1f))
        left.addView(label("UNIT / NETWORK",15,Color.rgb(0,229,255)))
        val roleRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        roleRow.addView(button("FIREFIGHTER",Color.rgb(0,125,210)){role="FIREFIGHTER"},LinearLayout.LayoutParams(0,48,1f))
        roleRow.addView(button("COMMAND",Color.rgb(130,55,220)){role="COMMANDER"},LinearLayout.LayoutParams(0,48,1f));left.addView(roleRow)
        unitInput=EditText(this).apply{hint="Unit ID";setText("FF-01");setSingleLine(true);textSize=16f;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setBackgroundColor(Color.rgb(26,37,61))}
        left.addView(unitInput,lp(48))
        statusView=label("MESH OFFLINE",13,Color.rgb(255,195,60));peersView=label("CONNECTED UNITS: 0",13,Color.WHITE)
        left.addView(statusView);left.addView(peersView)
        left.addView(button("START / RESTART MESH",Color.rgb(0,125,150)){startMesh()},lp(48))
        left.addView(button("SET GPS ANCHOR (OPTIONAL)",Color.rgb(45,70,100)){requestGpsBase()},lp(48))
        left.addView(label("GPS is optional after anchoring. Indoor coordinates drift over time.",10,Color.LTGRAY))
        mid.addView(label("POSITION ESTIMATE",15,Color.rgb(0,229,255)))
        positionView=label("E 0.0 m  N 0.0 m\nFLOOR 0  •  ±2.0 m",18,Color.WHITE)
        mid.addView(positionView)
        mid.addView(button("SET ENTRANCE / RESET",Color.rgb(0,145,105)){if(tracker==null)startTracking();tracker?.setEntranceReference();x=0.0;y=0.0;floor=0;uncertainty=1.0;trail.clear();trail.add(Point3(0.0,0.0,0.0));updatePosition("Entrance origin reset.")},lp(52))
        mid.addView(button("START SENSOR TRACKING",Color.rgb(0,125,210)){startTracking()},lp(52))
        mid.addView(button("PAUSE TRACKING",Color.rgb(190,75,40)){tracker?.stop();tracker=null;appendLog("Tracking paused")},lp(52))
        mid.addView(button("OPEN 3D MAP  ↗",Color.rgb(130,55,220)){show3DMap()},lp(62))
        mid.addView(label("No 20 m calibration required. Default stride estimate is approximate and uncertainty grows.",10,Color.LTGRAY))
        right.addView(label("QUICK ALERTS",15,Color.rgb(0,229,255)))
        listOf("SOS • NEED HELP","LOW AIR","NEED SUPPORT","EVACUATE","REGROUP","I AM OK").forEachIndexed{index,msg->
            val colors=listOf(Color.rgb(220,35,65),Color.rgb(210,90,30),Color.rgb(200,120,20),Color.rgb(190,40,100),Color.rgb(70,75,190),Color.rgb(0,135,95))
            right.addView(button(msg,colors[index]){mesh?.sendText(msg);appendLog("Sent: $msg")},LinearLayout.LayoutParams(-1,0,1f).apply{setMargins(0,2,0,2)})
        }
        val bottom=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
        dashboard.addView(bottom,LinearLayout.LayoutParams(-1,34))
        logView=label("READY • GPS ANCHOR OPTIONAL • PROTOTYPE ONLY",10,Color.LTGRAY)
        bottom.addView(logView,LinearLayout.LayoutParams(0,-1,1f))
        bottom.addView(label("NOT CERTIFIED FOR LIFE SAFETY",10,Color.rgb(255,90,110)))
        root.addView(dashboard,LinearLayout.LayoutParams(-1,-1))
    }

    private fun show3DMap(){
        root.removeAllViews()
        mapScreen=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(6,10,24))}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
        bar.addView(label("VYTAL • 3D TRACK MAP",20,Color.WHITE),LinearLayout.LayoutParams(0,52,1f))
        bar.addView(button("BACK TO DASHBOARD",Color.rgb(0,125,210)){showDashboard()},LinearLayout.LayoutParams(220,52))
        mapScreen.addView(bar)
        mapView=Map3DView()
        mapScreen.addView(mapView,LinearLayout.LayoutParams(-1,0,1f))
        val info=label("3D trajectory + estimated floors • sensors do not detect walls or room geometry",11,Color.rgb(0,229,255))
        mapScreen.addView(info,LinearLayout.LayoutParams(-1,28))
        root.addView(mapScreen,LinearLayout.LayoutParams(-1,-1))
        mapView.invalidate()
    }
    private fun showDashboard(){root.removeAllViews();root.addView(dashboard,LinearLayout.LayoutParams(-1,-1))}
    override fun onBackPressed(){if(::mapScreen.isInitialized && root.indexOfChild(mapScreen)>=0)showDashboard() else super.onBackPressed()}

    private fun startMesh(){
        if(requiredPermissions.any{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}){requestPermissions(requiredPermissions,101);return}
        val id=unitInput.text.toString().trim().ifBlank{"UNIT"};mesh?.stop()
        mesh=NearbyMesh(this,"$role-$id",
            {s->runOnUiThread{statusView.text="MESH: $s"}},
            {m->runOnUiThread{appendLog(m)}},
            {n->runOnUiThread{peersView.text="CONNECTED UNITS: $n"}},
            {unit,px,py,pf,pu->runOnUiThread{team[unit]=TeamPosition(px,py,pf,pu);if(::mapView.isInitialized)mapView.invalidate();appendLog("Position received: $unit • floor $pf")}},
            {lat,lon->runOnUiThread{baseLat=lat;baseLon=lon;appendLog("GPS anchor received")}})
        mesh?.start()
    }

    private fun requestGpsBase(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(requiredPermissions,101);return}
        try{
            gpsManager=getSystemService(LOCATION_SERVICE) as LocationManager;gpsSamples.clear();gpsListener?.let{gpsManager?.removeUpdates(it)}
            gpsListener=object:LocationListener{
                override fun onLocationChanged(l:Location){if(l.hasAccuracy()&&l.accuracy<=25f)gpsSamples.add(l)
                    statusView.text="GPS ANCHOR: ${gpsSamples.size}/5 fixes"
                    if(gpsSamples.size>=5){val a=gpsSamples.take(5);baseLat=a.map{it.latitude}.average();baseLon=a.map{it.longitude}.average();gpsManager?.removeUpdates(this);mesh?.sendBase(baseLat,baseLon);statusView.text="GPS ANCHOR SAVED";appendLog("Outdoor GPS anchor saved")}}
                override fun onProviderEnabled(p:String){}
                override fun onProviderDisabled(p:String){statusView.text="GPS disabled"}
            }
            gpsManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,gpsListener!!)
        }catch(e:Exception){appendLog("GPS error: ${e.localizedMessage}")}
    }

    private fun startTracking(){
        if(tracker!=null)return
        tracker=IndoorTracker(this,{px,py,pf,hdg,unc,steps->runOnUiThread{
            x=px;y=py;floor=pf;heading=hdg;uncertainty=unc
            val z=pf*3.2
            if(trail.isEmpty()||kotlin.math.hypot(trail.last().x-x,trail.last().y-y)>0.15||trail.last().z!=z)trail.add(Point3(x,y,z))
            if(trail.size>2500)trail.removeAt(0)
            updatePosition("Steps $steps • heading ${hdg.toInt()}° • ±%.1f m".format(unc))
            mesh?.sendPosition(x,y,floor,unc,baseLat,baseLon)
            if(::mapView.isInitialized)mapView.invalidate()
        }},{e->runOnUiThread{appendLog(e)}})
        tracker?.start();appendLog("Sensor tracking started. No distance calibration required.")
    }
    private fun updatePosition(detail:String){
        val global=if(!baseLat.isNaN()&&!baseLon.isNaN())"GPS anchor: %.6f, %.6f".format(baseLat+y/111111.0,baseLon+x/(111111.0*cos(Math.toRadians(baseLat)).coerceAtLeast(.01))) else "GPS anchor optional / not set"
        positionView.text="E %.1f m   N %.1f m   FLOOR %d\n%s\n%s".format(x,y,floor,global,detail)
        if(::mapView.isInitialized)mapView.invalidate()
    }
    private fun appendLog(s:String){if(::logView.isInitialized)logView.text=s}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==101){if(requiredPermissions.all{checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED})statusView.text="PERMISSIONS READY" else statusView.text="Permissions needed"}}
    override fun onDestroy(){tracker?.stop();gpsListener?.let{gpsManager?.removeUpdates(it)};mesh?.stop();super.onDestroy()}

    private fun label(t:String,size:Int,c:Int)=TextView(this).apply{text=t;textSize=size.toFloat();setTextColor(c);setPadding(5,4,5,4)}
    private fun button(t:String,color:Int,a:()->Unit)=Button(this).apply{text=t;textSize=12f;isAllCaps=false;setTextColor(Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(color);minHeight=44;setOnClickListener{a()}}
    private fun lp(h:Int)=LinearLayout.LayoutParams(-1,h).apply{setMargins(0,3,0,3)}

    private inner class Map3DView:View(this@MainActivity){
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private fun project(pt:Point3,cx:Float,cy:Float,scale:Float):Pair<Float,Float>{
            // Isometric 3D projection: north/east are horizontal axes; floor height is vertical.
            val sx=cx+(pt.x- x).toFloat()*scale*.82f-(pt.y-y).toFloat()*scale*.52f
            val sy=cy+(pt.x-x).toFloat()*scale*.30f+(pt.y-y).toFloat()*scale*.30f-pt.z.toFloat()*scale*.95f
            return sx to sy
        }
        override fun onDraw(c:Canvas){
            super.onDraw(c);c.drawColor(Color.rgb(6,10,24));val w=width.toFloat();val h=height.toFloat()
            // Glowing-style grid and floor planes
            p.strokeWidth=1f;p.color=Color.rgb(25,53,85)
            val cx=w*.5f;val cy=h*.62f;val scale=min(w,h)/22f
            for(i in -12..12){val a=project(Point3(i*2.0,-24.0,0.0),cx,cy,scale);val b=project(Point3(i*2.0,24.0,0.0),cx,cy,scale);c.drawLine(a.first,a.second,b.first,b.second,p)
                val d=project(Point3(-24.0,i*2.0,0.0),cx,cy,scale);val e=project(Point3(24.0,i*2.0,0.0),cx,cy,scale);c.drawLine(d.first,d.second,e.first,e.second,p)}
            for(f in -2..8){val z=f*3.2;val a=project(Point3(-22.0,-22.0,z),cx,cy,scale);val b=project(Point3(22.0,-22.0,z),cx,cy,scale);val d=project(Point3(22.0,22.0,z),cx,cy,scale);val e=project(Point3(-22.0,22.0,z),cx,cy,scale)
                p.color=if(f==floor)Color.rgb(0,120,135) else Color.rgb(18,37,59);p.style=Paint.Style.STROKE;p.strokeWidth=if(f==floor)2f else 1f
                val path=Path().apply{moveTo(a.first,a.second);lineTo(b.first,b.second);lineTo(d.first,d.second);lineTo(e.first,e.second);close()};c.drawPath(path,p)
                p.style=Paint.Style.FILL;p.color=Color.rgb(0,229,255);p.textSize=11f;c.drawText("FLOOR $f",a.first+5,a.second-3,p)}
            // Full 3D path trail
            if(trail.size>1){p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=Color.rgb(0,245,180);val path=Path()
                trail.forEachIndexed{i,pt->val q=project(pt,cx,cy,scale);if(i==0)path.moveTo(q.first,q.second)else path.lineTo(q.first,q.second)};c.drawPath(path,p);p.style=Paint.Style.FILL
                trail.filterIndexed{i,_->i%max(1,trail.size/100)==0}.forEach{pt->val q=project(pt,cx,cy,scale);p.color=Color.rgb(0,229,255);c.drawCircle(q.first,q.second,3f,p)}}
            val me=project(Point3(x,y,floor*3.2),cx,cy,scale);p.color=Color.rgb(255,55,110);c.drawCircle(me.first,me.second,9f,p);p.color=Color.WHITE;p.textSize=14f;c.drawText("YOU",me.first+12,me.second-8,p)
            team.forEach{(name,t)->val q=project(Point3(t.x,t.y,t.floor*3.2),cx,cy,scale);p.color=Color.rgb(255,180,45);c.drawCircle(q.first,q.second,7f,p);p.color=Color.WHITE;p.textSize=11f;c.drawText(name.take(10),q.first+9,q.second-6,p)}
            p.color=Color.rgb(0,229,255);p.textSize=13f;c.drawText("3D DEAD-RECKONED TRACK  •  FLOOR $floor  •  ±%.1f m".format(uncertainty),12f,h-14f,p)
            p.color=Color.LTGRAY;p.textSize=11f;c.drawText("Isometric view • vertical level is estimated from barometric pressure",12f,18f,p)
        }
    }
}
