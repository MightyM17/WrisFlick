package com.example.wristtype.presentation

import android.content.Context
import android.hardware.*
import kotlin.math.*

enum class Direction { N, NE, E, SE, S, SW, W, NW, CENTER, SHAKE }

class FlickClassifier(private val context: Context) : SensorEventListener {
    var onHoverAngle: ((Float) -> Unit)? = null

    private val R = FloatArray(9)
    private val ori = FloatArray(3)
    private var northOffset = 0f
    private var lastYaw = 0f

    fun calibrateNorth() { northOffset = lastYaw }

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro  = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotv  = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var onDirection: ((Direction) -> Unit)? = null
    private var lastTsNs = 0L
    private var omegaPeak = 0f
    private var vecX = 0f; private var vecY = 0f
    private var shakeEnergy = 0f

    // Exposed for UI guide
    var lastDirection: Direction? = null
        private set

    // Rotation matrix to transform device plane vectors to watch-face plane
//    private val R = FloatArray(9)

    fun start(callback: (Direction) -> Unit) {
        onDirection = callback
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(this, gyro,  SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(this, rotv,  SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        onDirection = null
        sm.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private var lastWindowTsNs = 0L
    private var lastEmitNs = 0L
    private val COOLDOWN_NS = 250_000_000L // 250 ms

    // thresholds (tune later in logger)
    private val FLICK_MIN_OMEGA = 4.0f   // was 2.5f
    private val MIN_VEC_FOR_DIR = 0.8f   // was 0.35f

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(R, e.values)
                SensorManager.getOrientation(R, ori)   // ori[0] = azimuth (yaw), 0 = North
                lastYaw = ori[0]
                onHoverAngle?.invoke(lastYaw - northOffset)
            }
            Sensor.TYPE_GYROSCOPE -> {
                // Gyro magnitude (rad/s) - useful to spot a quick flick
                val w = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                omegaPeak = max(omegaPeak, w)
                // Integrate simple direction vector projected onto screen plane using rotation
                // Derive screen axes from rotation matrix
                val x = e.values[0]; val y = e.values[1]
//                val z = e.values[2]
                // Project onto watch-face plane (approx: use x,y in device coords after rotation)
                // A more robust approach multiplies gyro vector by R; MVP keeps it simple:
                vecX += x; vecY += y

                // Time-based window: every ~200ms, decide if we saw a flick
                val now = e.timestamp
                if (lastTsNs == 0L) lastTsNs = now
                if ((now - lastTsNs) > 200_000_000L) { // 200 ms
                    classifyAndMaybeEmit(now)
                    lastTsNs = now
                    omegaPeak = 0f
                    vecX = 0f; vecY = 0f
                    // decay shake energy a bit
                    shakeEnergy *= 0.5f
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // crude shake detector (delete): strong acceleration spike
                val a = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (a > 27.0f) { // ~2.7 g threshold (tune)
                    shakeEnergy += (a - 27f)
                    if (shakeEnergy > 10f) emit(Direction.SHAKE)
                }
            }
        }
    }

    private fun classifyAndMaybeEmit(now: Long) {
        val mag = hypot(vecX, vecY)

        // If it doesn't look like a deliberate flick, treat as idle (do NOT emit CENTER)
        if (omegaPeak < FLICK_MIN_OMEGA || mag < MIN_VEC_FOR_DIR) {
            return
        }

        // Respect post-gesture cool-down
        if (now - lastEmitNs < COOLDOWN_NS) return

        val angle = atan2(vecY, vecX)
        val dir = angleToDirection(angle)

        lastEmitNs = now
        emit(dir)
    }

    private fun angleToDirection(theta: Float): Direction {
        val step = (Math.PI / 4).toFloat() // 45°
        val idx = (((theta + Math.PI) / step).roundToInt() % 8 + 8) % 8
        return when (idx) {
            0 -> Direction.W
            1 -> Direction.NW
            2 -> Direction.N
            3 -> Direction.NE
            4 -> Direction.E
            5 -> Direction.SE
            6 -> Direction.S
            else -> Direction.SW
        }
    }

    private fun emit(d: Direction) {
        lastDirection = d
        onDirection?.invoke(d)
    }
}
