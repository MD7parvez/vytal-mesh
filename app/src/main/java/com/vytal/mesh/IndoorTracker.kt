package com.vytal.mesh

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max

/**
 * Calibration-free dead-reckoning prototype. Uses Android step detector when present,
 * gyro + rotation vector for heading, accelerometer fallback and barometric floor estimates.
 * It estimates a 3D path; it cannot sense walls or reliably infer body direction from an arm mount.
 */
class IndoorTracker(context:Context,private val onUpdate:(Double,Double,Int,Double,Double,Int)->Unit,private val onEvent:(String)->Unit):SensorEventListener{
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val step=manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotation=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressure=manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accel=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var x=0.0;private var y=0.0;private var floor=0;private var heading=0.0
    private var stepLength=0.70;private var steps=0;private var baseline=Float.NaN;private var uncertainty=1.5
    private var lastStep=0L;private var lastPeak=0L;private var smoothed=9.8f;private var running=false
    private var gyroYaw=0.0;private var lastGyroNs=0L;private var lastRotationHeading=Double.NaN
    fun start(){if(running)return;running=true
        step?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        rotation?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        gyro?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        pressure?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
        if(step==null)accel?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        if(step==null)onEvent("Hardware step detector unavailable; accelerometer fallback is less reliable.")
        if(rotation==null&&gyro==null)onEvent("No usable heading sensor; path direction is unreliable.")
        if(gyro==null)onEvent("Gyroscope unavailable; rotation-vector heading only.")
        publish()
    }
    fun stop(){running=false;manager.unregisterListener(this)}
    fun setEntranceReference(){x=0.0;y=0.0;floor=0;uncertainty=1.0;baseline=Float.NaN;publish();onEvent("Entrance origin reset. No distance calibration needed.")}
    override fun onSensorChanged(e:SensorEvent){if(!running)return
        when(e.sensor.type){
            Sensor.TYPE_ROTATION_VECTOR->{val m=FloatArray(9);SensorManager.getRotationMatrixFromVector(m,e.values);val o=FloatArray(3);SensorManager.getOrientation(m,o)
                val h=(Math.toDegrees(o[0].toDouble())+360.0)%360.0
                if(lastRotationHeading.isNaN())gyroYaw=h else {val delta=((h-lastRotationHeading+540.0)%360.0)-180.0;gyroYaw=(gyroYaw+delta+360.0)%360.0}
                lastRotationHeading=h;heading=gyroYaw}
            Sensor.TYPE_GYROSCOPE->{if(rotation==null){if(lastGyroNs!=0L){val dt=(e.timestamp-lastGyroNs)/1_000_000_000.0;gyroYaw=(gyroYaw+Math.toDegrees(e.values[2].toDouble()*dt)+360.0)%360.0;heading=gyroYaw};lastGyroNs=e.timestamp}}
            Sensor.TYPE_STEP_DETECTOR->{if(e.values.isNotEmpty()&&e.values[0]>0f)countStep(e.timestamp)}
            Sensor.TYPE_ACCELEROMETER->{val mag=kotlin.math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);val d=abs(mag-smoothed);smoothed=smoothed*.8f+mag*.2f
                if(d>2.2f&&e.timestamp-lastPeak>380_000_000L){lastPeak=e.timestamp;countStep(e.timestamp)}}
            Sensor.TYPE_PRESSURE->{val v=e.values[0];if(baseline.isNaN())baseline=v
                val dz=baseline-v;val estimated=kotlin.math.round(dz/.36f).toInt()
                if(abs(dz)>.9f&&abs(estimated-floor)>=1){floor=estimated.coerceIn(-10,80);uncertainty+=.8;onEvent("Barometer estimates floor $floor; confirm against known building levels.");publish()}}
        }
    }
    private fun countStep(t:Long){if(lastStep!=0L&&t-lastStep<250_000_000L)return;lastStep=t;steps++
        // Generic stride estimate avoids a required 20 m calibration; it is not personalized.
        val a=Math.toRadians(heading);x+=stepLength*sin(a);y+=stepLength*cos(a)
        uncertainty+=.04+stepLength*.035
        if(steps%2==0)publish()
    }
    private fun publish(){onUpdate(x,y,floor,heading,uncertainty,steps)}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
}
