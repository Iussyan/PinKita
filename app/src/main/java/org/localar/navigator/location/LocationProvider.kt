package org.localar.navigator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.localar.core.geo.GeoPoint

enum class LocationQuality {
    GOOD,    // Accuracy <= 10m, age <= 5s
    USABLE,  // Accuracy <= 25m, age <= 10s
    POOR,    // Accuracy > 25m or age > 10s
    MISSING  // No fix available
}

data class LocationFix(
    val point: GeoPoint,
    val accuracyM: Float,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val elapsedRealtimeMs: Long,
    val quality: LocationQuality
)

/**
 * Foreground Location Provider conforming to Section 12.1 of spec.md.
 * Requests GNSS location updates at ~1 Hz during active navigation.
 */
class LocationProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _currentFix = MutableStateFlow<LocationFix?>(null)
    val currentFix: StateFlow<LocationFix?> = _currentFix.asStateFlow()

    private var isListening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val nowRealtimeMs = SystemClock.elapsedRealtime()
            val ageMs = nowRealtimeMs - (location.elapsedRealtimeNanos / 1_000_000L)
            val accuracy = location.accuracy

            val quality = when {
                accuracy <= 10.0f && ageMs <= 5000L -> LocationQuality.GOOD
                accuracy <= 25.0f && ageMs <= 10000L -> LocationQuality.USABLE
                else -> LocationQuality.POOR
            }

            _currentFix.value = LocationFix(
                point = GeoPoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    altitudeM = if (location.hasAltitude()) location.altitude else 0.0
                ),
                accuracyM = accuracy,
                bearingDeg = if (location.hasBearing()) location.bearing else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
                elapsedRealtimeMs = nowRealtimeMs,
                quality = quality
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startListening(): Boolean {
        if (isListening) return true

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            return false
        }

        try {
            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 Hz
                    0.5f,  // 0.5m minimum displacement
                    listener
                )
            }
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1.0f,
                    listener
                )
            }
            isListening = true
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            locationManager.removeUpdates(listener)
        } catch (_: SecurityException) {}
        isListening = false
    }
}
