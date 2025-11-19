package com.example.wristtype.presentation

import android.content.Context
import android.hardware.*
import kotlin.math.*

enum class Direction { CENTER, SHAKE }

class FlickClassifier(private val context: Context) : SensorEventListener {

    // === Public callbacks ===
    var onHoverAngle: ((Float) -> Unit)? = null   // radians, we send 4 discrete angles
    private var onDirection: ((Direction) -> Unit)? = null

    fun start(callback: (Direction) -> Unit) {
        onDirection = callback
        rotSensor?.also { sm.registerListener(this, it, 10_000) }
            ?: sm.registerListener(this, rotv!!, 10_000)
        sm.registerListener(this, gyro, 5_000)
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        onDirection = null
        sm.unregisterListener(this)
    }

    // Call this from a "Calibrate" chip in your UI
    fun calibrateNorth() {
        basePitch = pitchSmooth
        baseRoll  = rollSmooth
    }

    // === Sensors ===
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotSensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotv      = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro      = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel     = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // === Orientation: pitch/roll relative to neutral pose ===
    private val R = FloatArray(9)
    private val ori = FloatArray(3)   // [azimuth, pitch, roll]

    private var pitchSmooth = 0f
    private var rollSmooth  = 0f
    private var orientInit  = false

    // Saved neutral pose (set on calibrate)
    private var basePitch = 0f
    private var baseRoll  = 0f

    // Smoothing & thresholds (TUNED FOR WRIST-ONLY MOVES)
    private val ORIENT_ALPHA = 0.25f      // higher → reacts faster to wrist bends
    private val PITCH_THRESH = 0.12f      // rad ≈ 7° up/down
    private val ROLL_THRESH  = 0.12f      // rad ≈ 7° left/right
    private val MIN_CHANGE   = 0.06f      // rad ≈ 3.5° dead zone

    private enum class AimDir { NEUTRAL, UP, DOWN, LEFT, RIGHT }
    private var lastAim = AimDir.NEUTRAL

    // === Flick detection (gyro magnitude) ===
    private enum class FlickState { IDLE, ARMING, COOLDOWN }
    private var flickState      = FlickState.IDLE
    private var lastGyroNs      = 0L
    private var windowStartNs   = 0L
    private var cooldownUntilNs = 0L
    private var peakOmega       = 0f

    private val START_OMEGA = 1.8f
    private val PEAK_OMEGA  = 2.8f
    private val END_OMEGA   = 1.2f
    private val WINDOW_NS   = 120_000_000L   // 120 ms
    private val COOLDOWN_NS = 260_000_000L   // 260 ms

    // Shake-to-delete
    private var shakeEnergy = 0f

    private fun fire(dir: Direction) {
        onDirection?.invoke(dir)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR -> handleRotation(e)
            Sensor.TYPE_GYROSCOPE -> handleGyro(e)
            Sensor.TYPE_ACCELEROMETER -> handleAccel(e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --------- 1) ORIENTATION → 4 DISCRETE DIRECTIONS ---------
    private fun handleRotation(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(R, e.values)
        SensorManager.getOrientation(R, ori)
        val rawPitch = ori[1]  // flex/extend at wrist (palm up/down)
        val rawRoll  = ori[2]  // radial/ulnar deviation (palm left/right)

        if (!orientInit) {
            pitchSmooth = rawPitch
            rollSmooth  = rawRoll
            orientInit  = true
        } else {
            pitchSmooth += ORIENT_ALPHA * (rawPitch - pitchSmooth)
            rollSmooth  += ORIENT_ALPHA * (rawRoll  - rollSmooth)
        }

        val dPitch = pitchSmooth - basePitch
        val dRoll  = rollSmooth  - baseRoll

        // Small movement → stay NEUTRAL (no change)
        if (abs(dPitch) < MIN_CHANGE && abs(dRoll) < MIN_CHANGE) {
            return
        }

        val newAim: AimDir = if (abs(dPitch) >= abs(dRoll)) {
            // Vertical wrist bend dominates
            when {
                dPitch <= -PITCH_THRESH -> AimDir.UP      // extend wrist a bit (watch top away)
                dPitch >=  PITCH_THRESH -> AimDir.DOWN    // flex wrist (watch bottom away)
                else -> lastAim
            }
        } else {
            // Horizontal wrist bend dominates
            when {
                dRoll >=  ROLL_THRESH  -> AimDir.RIGHT     // bend toward thumb side
                dRoll <= -ROLL_THRESH  -> AimDir.LEFT      // bend toward little-finger side
                else -> lastAim
            }
        }

        if (newAim == lastAim) return
        lastAim = newAim

        val angle = when (newAim) {
            AimDir.UP    -> 0f                               // top
            AimDir.RIGHT -> (Math.PI / 2).toFloat()          // right
            AimDir.DOWN  -> Math.PI.toFloat()                // bottom
            AimDir.LEFT  -> (-Math.PI / 2).toFloat()         // left
            AimDir.NEUTRAL -> 0f
        }

        onHoverAngle?.invoke(angle)
    }

    // --------- 2) FLICK → SELECT CURRENT DIRECTION ---------
    private fun handleGyro(e: SensorEvent) {
        val now = e.timestamp
        if (lastGyroNs == 0L) lastGyroNs = now

        val gx = e.values[0]
        val gy = e.values[1]
        val gz = e.values[2]
        val omega = sqrt(gx*gx + gy*gy + gz*gz)

        when (flickState) {
            FlickState.IDLE -> {
                if (now >= cooldownUntilNs && omega > START_OMEGA) {
                    flickState = FlickState.ARMING
                    windowStartNs = now
                    peakOmega = omega
                }
            }
            FlickState.ARMING -> {
                peakOmega = max(peakOmega, omega)
                val elapsed = now - windowStartNs
                val endWindow =
                    (omega < END_OMEGA && elapsed > 30_000_000L) ||
                            (elapsed > WINDOW_NS)

                if (endWindow) {
                    if (peakOmega >= PEAK_OMEGA) {
                        fire(Direction.CENTER)   // confirm current direction
                        flickState = FlickState.COOLDOWN
                        cooldownUntilNs = now + COOLDOWN_NS
                    } else {
                        flickState = FlickState.IDLE
                    }
                }
            }
            FlickState.COOLDOWN -> {
                if (now >= cooldownUntilNs && omega < END_OMEGA) {
                    flickState = FlickState.IDLE
                }
            }
        }

        lastGyroNs = now
    }

    // --------- 3) SHAKE → DELETE LAST WORD ---------
    private fun handleAccel(e: SensorEvent) {
        val ax = e.values[0]
        val ay = e.values[1]
        val az = e.values[2]
        val a = sqrt(ax*ax + ay*ay + az*az)

        if (a > 27f) { // ~2.7 g
            shakeEnergy += (a - 27f)
            if (shakeEnergy > 12f) {
                fire(Direction.SHAKE)
                shakeEnergy = 0f
            }
        } else {
            shakeEnergy *= 0.9f
        }
    }
}
