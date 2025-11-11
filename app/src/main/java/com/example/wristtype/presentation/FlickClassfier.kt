package com.example.wristtype.presentation

import android.content.Context
import android.hardware.*
import kotlin.math.*

enum class Direction { CENTER, SHAKE }

class FlickClassifier(private val context: Context) : SensorEventListener {

    // === Callbacks ===
    var onHoverAngle: ((Float) -> Unit)? = null
    private var onDirection: ((Direction) -> Unit)? = null

    fun start(callback: (Direction) -> Unit) {
        onDirection = callback
        // Prefer GAME_ROTATION_VECTOR (no magnet)
        gameRot?.also { sm.registerListener(this, it, 10_000) }
            ?: sm.registerListener(this, rotv!!, 10_000)
        sm.registerListener(this, gyro, 5_000)
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
    }
    fun stop() { onDirection = null; sm.unregisterListener(this) }

    // === Sensors ===
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gameRot = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotv    = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro    = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel   = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // === Tilt-based aim (pitch/roll) ===
    private val R9 = FloatArray(9)
    private val euler = FloatArray(3)      // [yaw, pitch, roll]
    private var aim = 0f                   // unwrapped aim (rad), 0 = up/N
    private var aimSmooth = 0f
    private var aimInit = false
    private var aimZero = 0f               // calibration offset

    // Feel tuning (adjust if needed)
    private val AIM_ALPHA   = 0.14f        // smoothing (higher = snappier)
    private val AIM_GAIN    = 2f        // boosts sweep so all octants are reachable
    private val AIM_DEADZONE= 0.03f        // rad (~1.7°) ignore micro jitter

    fun calibrateNorth() { aimZero = aimSmooth } // tap in neutral pose

    // === Flick detection (simple, robust) ===
    private enum class S { IDLE, ARMING, COOLDOWN }
    private var s = S.IDLE
    private var lastNs = 0L
    private var startNs = 0L
    private var coolUntil = 0L
    private var peakOmega = 0f

    // Flick thresholds (good GW4 defaults)
    private val START_OMEGA  = 1.8f        // arm when |ω| >
    private val PEAK_OMEGA   = 2.8f        // must hit this peak
    private val END_OMEGA    = 1.2f
    private val WINDOW_NS    = 120_000_000L// 120 ms
    private val COOLDOWN_NS  = 260_000_000L

    // Shake to delete (optional)
    private var shakeEnergy = 0f

    private fun fire(d: Direction) { onDirection?.invoke(d) }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {

            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                // --- Twist-invariant palm aiming with LEFT reach assist ---
                SensorManager.getRotationMatrixFromVector(R9, e.values)

// Device Z-axis (screen normal) in world coords = 3rd column of R
                val nx = R9[2]
                val ny = R9[5]
// project onto horizontal plane
                var vx = nx
                var vy = ny

// -------- REACH ASSIST (make WEST / NORTH-WEST easier) --------
// (1) Anisotropic scaling: amplify horizontal component on the LEFT half-plane
                val KX_RIGHT = 1.15f   // slight boost on right
                val KX_LEFT  = 1.65f   // stronger boost on left
                val KY_ALL   = 1.00f
                vx *= if (vx < 0f) KX_LEFT else KX_RIGHT
                vy *= KY_ALL

// (2) Micro-bias toward the left to widen W/NW sectors a hair (~6°)
                val LEFT_BIAS_RAD = 0.10f
                var aimNow = atan2(vx.toDouble(), vy.toDouble()).toFloat()
                if (vx < 0f) aimNow += LEFT_BIAS_RAD

// Unwrap + smooth
                if (!aimInit) { aim = aimNow; aimSmooth = aimNow; aimInit = true }
                else {
                    var d = aimNow - aim
                    if (d >  Math.PI)  d -= (2*Math.PI).toFloat()
                    if (d < -Math.PI)  d += (2*Math.PI).toFloat()
                    aim += d
                    aimSmooth += AIM_ALPHA * (aim - aimSmooth)   // keep your AIM_ALPHA ~0.14
                }

// Asymmetric deadzone: make left side a bit more permissive
                val DEADZONE_RIGHT = 0.04f
                val DEADZONE_LEFT  = 0.02f
                val projMag = hypot(vx, vy)
                val dz = if (vx < 0f) DEADZONE_LEFT else DEADZONE_RIGHT

                val visAngle = if (projMag < dz) 0f else (aimSmooth - aimZero) * AIM_GAIN  // keep your AIM_GAIN (e.g., 1.35f)
                onHoverAngle?.invoke(visAngle)  // negate here if rotation feels backward

            }

            Sensor.TYPE_GYROSCOPE -> {
                val now = e.timestamp
                if (lastNs == 0L) lastNs = now
                val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                val omega = sqrt(gx*gx + gy*gy + gz*gz)

                when (s) {
                    S.IDLE -> if (now >= coolUntil && omega > START_OMEGA) {
                        s = S.ARMING
                        startNs = now
                        peakOmega = omega
                    }

                    S.ARMING -> {
                        peakOmega = max(peakOmega, omega)
                        val elapsed = now - startNs
                        val end = (omega < END_OMEGA && elapsed > 30_000_000L) || elapsed > WINDOW_NS
                        if (end) {
                            if (peakOmega >= PEAK_OMEGA) {
                                fire(Direction.CENTER)        // select highlighted slice
                                s = S.COOLDOWN
                                coolUntil = now + COOLDOWN_NS
                            } else s = S.IDLE
                        }
                    }

                    S.COOLDOWN -> if (now >= coolUntil && omega < END_OMEGA) s = S.IDLE
                }
                lastNs = now
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // optional shake-to-delete
                val a = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (a > 27f) { shakeEnergy += (a - 27f); if (shakeEnergy > 12f) { fire(Direction.SHAKE); shakeEnergy = 0f } }
                else shakeEnergy *= 0.9f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
