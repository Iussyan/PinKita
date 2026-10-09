package org.localar.core.geo

import kotlin.math.*

/**
 * Standard WGS84 Geographic Coordinate.
 * Latitude and Longitude in degrees.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double = 0.0
) {
    init {
        require(lat in -90.0..90.0) { "Latitude must be between -90 and 90 degrees: $lat" }
        require(lon in -180.0..180.0) { "Longitude must be between -180 and 180 degrees: $lon" }
    }

    companion object {
        const val WGS84_EARTH_RADIUS_M = 6378137.0
    }
}

/**
 * Local East-North-Up coordinate in metres relative to a local origin GeoPoint.
 */
data class EnuPoint(
    val eastM: Double,
    val northM: Double,
    val upM: Double = 0.0
) {
    fun distance2d(other: EnuPoint): Double {
        val de = eastM - other.eastM
        val dn = northM - other.northM
        return sqrt(de * de + dn * dn)
    }

    fun distance3d(other: EnuPoint): Double {
        val de = eastM - other.eastM
        val dn = northM - other.northM
        val du = upM - other.upM
        return sqrt(de * de + dn * dn + du * du)
    }
}

/**
 * Geodesic distance and ENU projection utilities.
 * Implements Section 10.3 and Section 13.2 of spec.md.
 */
object GeoUtils {
    private const val R = GeoPoint.WGS84_EARTH_RADIUS_M

    /**
     * Great-circle distance between two geographic points in metres using Haversine formula.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = Math.toRadians(p2.lat - p1.lat)
        val dLon = Math.toRadians(p2.lon - p1.lon)
        val lat1Rad = Math.toRadians(p1.lat)
        val lat2Rad = Math.toRadians(p2.lat)

        val a = sin(dLat / 2.0).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return R * c
    }

    /**
     * Initial bearing (forward azimuth) from p1 to p2 in degrees [0, 360).
     */
    fun bearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.lat)
        val lat2 = Math.toRadians(p2.lat)
        val dLon = Math.toRadians(p2.lon - p1.lon)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    /**
     * Local tangent plane conversion (Section 13.2 of spec.md).
     * Valid for local horizons (< 1 km from origin).
     */
    fun toEnu(point: GeoPoint, origin: GeoPoint): EnuPoint {
        val phi0 = Math.toRadians(origin.lat)
        val dPhi = Math.toRadians(point.lat - origin.lat)
        val dLambda = Math.toRadians(point.lon - origin.lon)

        val east = R * cos(phi0) * dLambda
        val north = R * dPhi
        val up = point.altitudeM - origin.altitudeM

        return EnuPoint(eastM = east, northM = north, upM = up)
    }

    /**
     * Converts local ENU back to WGS84 geographic coordinate.
     */
    fun fromEnu(enu: EnuPoint, origin: GeoPoint): GeoPoint {
        val phi0 = Math.toRadians(origin.lat)
        val dPhi = enu.northM / R
        val dLambda = enu.eastM / (R * cos(phi0))

        val lat = origin.lat + Math.toDegrees(dPhi)
        val lon = origin.lon + Math.toDegrees(dLambda)
        val alt = origin.altitudeM + enu.upM

        return GeoPoint(lat = lat, lon = lon, altitudeM = alt)
    }

    /**
     * Normalized circular difference between two angles in degrees in range [-180, 180).
     */
    fun angleDeltaDegrees(targetAngle: Double, currentAngle: Double): Double {
        var diff = (targetAngle - currentAngle + 180.0) % 360.0
        if (diff < 0) diff += 360.0
        return diff - 180.0
    }
}
