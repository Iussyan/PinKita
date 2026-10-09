package org.localar.navigator.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.localar.core.geo.GeoPoint

/**
 * Device orientation and true-north heading provider conforming to Section 12.2 of spec.md.
 * Translates device coordinates to rear camera viewing direction and applies geomagnetic declination.
 */
class HeadingProvider(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _headingDeg = MutableStateFlow(0f)
    val headingDeg: StateFlow<Float> = _headingDeg.asStateFlow()

    private var currentFix: GeoPoint? = null
    private var isListening = false

    fun updateLocation(fix: GeoPoint) {
        currentFix = fix
    }

    fun startListening() {
        if (isListening || rotationSensor == null) return
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap coordinates for portrait display: X -> East, Y -> North/Up
        val remappedMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // Azimuth in degrees [0, 360)
        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuthDeg < 0f) {
            azimuthDeg += 360f
        }

        // Apply geomagnetic declination if GPS fix is available
        val declinationDeg = currentFix?.let { loc ->
            val field = GeomagneticField(
                loc.lat.toFloat(),
                loc.lon.toFloat(),
                loc.altitudeM.toFloat(),
                System.currentTimeMillis()
            )
            field.declination
        } ?: 0f

        val trueNorthHeading = (azimuthDeg + declinationDeg + 360f) % 360f
        _headingDeg.value = trueNorthHeading
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
