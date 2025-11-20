package com.example.wristtype.presentation

import android.content.Context
import android.hardware.*
import kotlin.math.*

enum class Direction { CENTER, SHAKE }   // CENTER = "select", SHAKE = "delete"

class FlickClassifier(private val context: Context) : SensorEventListener {

    // === Public callbacks ===
    var onHoverAngle: ((Float) -> Unit)? = null   // radians; we send 4 discrete values
    private var onDirection: ((Direction) -> Unit)? = null

    fun start(callback: (Direction) -> Unit) {
        onDirection = callback
        rotSensor?.also { sm.registerListener(this, it, 10_000) }
            ?: sm.registerListener(this, rotv!!, 10_000)
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

    // Smoothing & thresholds (tuned for wrist-only bends)
    private val ORIENT_ALPHA = 0.25f      // smoothing (higher → snappier)
    private val PITCH_THRESH = 0.12f      // rad ≈ 7° up/down
    private val ROLL_THRESH  = 0.12f      // rad ≈ 7° left/right
    private val MIN_CHANGE   = 0.06f      // rad ≈ 3.5° dead zone near neutral

    private enum class AimDir { NEUTRAL, UP, DOWN, LEFT, RIGHT }
    private var lastAim = AimDir.NEUTRAL

    // === "Clench" detection (accelerometer burst) ===
    private var lastAccelNs = 0L
    private var clenchStartNs = 0L
    private var clenchPeak = 0f
    private var inClenchWindow = false
    private var clenchCooldownUntil = 0L

    // thresholds (tune based on feel)
    private val CLENCH_START_THRESH = 10f        // ~1.4 g: start clench window
    private val CLENCH_PEAK_THRESH  = 14f        // ~1.7 g: consider it a real clench
    private val CLENCH_WINDOW_NS    = 150_000_000L // 150 ms
    private val CLENCH_COOLDOWN_NS  = 300_000_000L // 300 ms between clench events

    // Shake-to-delete (stronger / repeated movement)
    private var shakeEnergy = 0f
    private val SHAKE_ACCEL_THRESH = 16f    // ~2.4 g
    private val SHAKE_ENERGY_THRESH = 18f   // integrated energy threshold

    private fun fire(dir: Direction) {
        onDirection?.invoke(dir)
    }

    // --- Sensor dispatch ---
    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR -> handleRotation(e)
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

        // Small movement → stay NEUTRAL (no snap change)
        if (abs(dPitch) < MIN_CHANGE && abs(dRoll) < MIN_CHANGE) {
            return
        }

        val newAim: AimDir = if (abs(dPitch) >= abs(dRoll)) {
            // Vertical wrist bend dominates
            when {
                dPitch <= -PITCH_THRESH -> AimDir.UP      // extend wrist (top edge away)
                dPitch >=  PITCH_THRESH -> AimDir.DOWN    // flex wrist (bottom edge away)
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

        // Map 4 directions to fixed ring angles
        val angle = when (newAim) {
            AimDir.UP    -> 0f                                 // top
            AimDir.RIGHT -> (Math.PI / 2).toFloat()            // right
            AimDir.DOWN  -> Math.PI.toFloat()                  // bottom
            AimDir.LEFT  -> (-Math.PI / 2).toFloat()           // left
            AimDir.NEUTRAL -> 0f
        }

        onHoverAngle?.invoke(angle)
    }

    // --------- 2) ACCEL: CLENCH → SELECT, SHAKE → DELETE ---------
    private fun handleAccel(e: SensorEvent) {
        val ax = e.values[0]
        val ay = e.values[1]
        val az = e.values[2]
        val aMag = sqrt(ax*ax + ay*ay + az*az)

        val now = e.timestamp
        if (lastAccelNs == 0L) lastAccelNs = now

        // --- Clench detection (quick, strong bump) ---
        if (!inClenchWindow && now >= clenchCooldownUntil && aMag > CLENCH_START_THRESH) {
            // Start a clench window
            inClenchWindow = true
            clenchStartNs = now
            clenchPeak = aMag
        } else if (inClenchWindow) {
            // Update peak
            clenchPeak = max(clenchPeak, aMag)
            val elapsed = now - clenchStartNs

            val endWindow = elapsed > CLENCH_WINDOW_NS || aMag < 9f // roughly back near 1 g
            if (endWindow) {
                if (clenchPeak >= CLENCH_PEAK_THRESH) {
                    // Treat this as "close wrist / clench" → SELECT current direction
                    fire(Direction.CENTER)
                    clenchCooldownUntil = now + CLENCH_COOLDOWN_NS
                }
                inClenchWindow = false
                clenchPeak = 0f
            }
        }

        // --- Shake-to-delete (stronger, repeated) ---
        if (aMag > SHAKE_ACCEL_THRESH) {
            shakeEnergy += (aMag - SHAKE_ACCEL_THRESH)
            if (shakeEnergy > SHAKE_ENERGY_THRESH) {
                fire(Direction.SHAKE)
                shakeEnergy = 0f
            }
        } else {
            shakeEnergy *= 0.9f
        }

        lastAccelNs = now
    }
}
