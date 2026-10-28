package com.vytal.mesh

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

/** Experimental phone-sensor dead reckoning. Arm motion causes errors; not navigation-grade. */
class IndoorTracker(context:Context, private val onUpdate:(Double,Double,Int,Double,Double,Int)->Unit, private val onEvent:(String)->Unit):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val step=manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotation=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val pressure=manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accel=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var x=0.0;private var y=0.0;private var floor=0;private var heading=0.0
    private var stepLength=0.70;private var steps=0
    private var calibration=-1;private var baseline=Float.NaN;private var uncertainty=1.5
    private var lastStep=0L;private var lastPeak=0L;private var smoothed=9.8f;private var running=false
    fun start(){if(running)return;running=true
        step?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        rotation?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        pressure?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
        if(step==null)accel?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        if(step==null)onEvent("Step detector unavailable: accelerometer fallback may count arm swings as steps.")
        if(rotation==null)onEvent("Rotation-vector sensor unavailable; heading may be unreliable.")
        publish()
    }
    fun stop(){running=false;manager.unregisterListener(this)}
    fun setEntranceReference(){x=0.0;y=0.0;uncertainty=1.0;publish();onEvent("Entrance coordinate reset. Heading uses the sensor azimuth from north.")}
    fun beginStepCalibration(){calibration=steps;onEvent("Walk exactly 20 metres now, then finish calibration.")}
    fun finishStepCalibration():Double?{if(calibration<0){onEvent("Press BEGIN calibration first.");return null};val n=steps-calibration;calibration=-1;if(n<5){onEvent("Calibration rejected: fewer than five steps detected.");return null};stepLength=20.0/n;onEvent("Calibrated step length using $n detected steps.");return stepLength}
    override fun onSensorChanged(e:SensorEvent){if(!running)return
        when(e.sensor.type){
            Sensor.TYPE_ROTATION_VECTOR->{val m=FloatArray(9);SensorManager.getRotationMatrixFromVector(m,e.values);val o=FloatArray(3);SensorManager.getOrientation(m,o);heading=(Math.toDegrees(o[0].toDouble())+360.0)%360.0}
            Sensor.TYPE_STEP_DETECTOR->{if(e.values.isNotEmpty()&&e.values[0]>0f)countStep(e.timestamp)}
            Sensor.TYPE_ACCELEROMETER->{val mag=kotlin.math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);val d=abs(mag-smoothed);smoothed=smoothed*.8f+mag*.2f;if(d>2.2f&&e.timestamp-lastPeak>350_000_000L){lastPeak=e.timestamp;countStep(e.timestamp)}}
            Sensor.TYPE_PRESSURE->{val v=e.values[0];if(baseline.isNaN())baseline=v;val dz=baseline-v;val estimate=kotlin.math.round(dz/.36f).toInt();if(abs(estimate-floor)>=1&&abs(dz)>.5f){floor=estimate.coerceIn(-10,80);uncertainty+=1.0;onEvent("Barometer estimates floor $floor; verify manually.");publish()}}
        }
    }
    private fun countStep(t:Long){if(lastStep!=0L&&t-lastStep<250_000_000L)return;lastStep=t;steps++;val a=Math.toRadians(heading);x+=stepLength*sin(a);y+=stepLength*cos(a);uncertainty+=.035+stepLength*.025;if(steps%2==0)publish()}
    private fun publish(){onUpdate(x,y,floor,heading,uncertainty,steps)}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
}
