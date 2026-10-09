package org.localar.core.geo

import kotlin.math.*

/**
 * 3D Float vector used for local AR rendering and coordinate transformations.
 */
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val l = length()
        return if (l > 1e-6f) Vector3(x / l, y / l, z / l) else Vector3(0f, 0f, 0f)
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val UP = Vector3(0f, 1f, 0f)

        fun cross(a: Vector3, b: Vector3): Vector3 {
            return Vector3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
            )
        }

        fun dot(a: Vector3, b: Vector3): Float = a.x * b.x + a.y * b.y + a.z * b.z
    }
}

/**
 * Alignment transformation from local geographic ENU coordinates to ARCore world frame coordinates.
 * Implements Section 13.3 of spec.md.
 */
class ArAligner(
    val geoOrigin: GeoPoint,
    val arOriginW: Vector3,
    val headingThetaRad: Double,
    val cameraForwardW: Vector3
) {
    val eastW: Vector3
    val northW: Vector3
    val upW: Vector3 = Vector3.UP

    init {
        // 1. Horizontal projection of camera forward vector
        val fHorizontal = Vector3(cameraForwardW.x, 0f, cameraForwardW.z).normalized()
        val fW = if (fHorizontal.length() > 1e-4f) fHorizontal else Vector3(0f, 0f, -1f)

        // 2. Right vector in horizontal plane
        val rW = Vector3.cross(fW, upW).normalized()

        // 3. East and North vectors mapped into ARCore world frame
        val sinT = sin(headingThetaRad).toFloat()
        val cosT = cos(headingThetaRad).toFloat()

        eastW = (fW * sinT) + (rW * cosT)
        northW = (fW * cosT) - (rW * sinT)
    }

    /**
     * Projects local ENU coordinates to ARCore 3D world coordinates.
     */
    fun enuToWorld(enu: EnuPoint): Vector3 {
        val e = enu.eastM.toFloat()
        val n = enu.northM.toFloat()
        val u = enu.upM.toFloat()

        return arOriginW + (eastW * e) + (northW * n) + (upW * u)
    }

    /**
     * Projects a WGS84 geographic coordinate into ARCore world frame.
     */
    fun geoToWorld(geoPoint: GeoPoint): Vector3 {
        val enu = GeoUtils.toEnu(geoPoint, geoOrigin)
        return enuToWorld(enu)
    }

    /**
     * Inverse projection: maps ARCore 3D world coordinate back to WGS84 geographic coordinate.
     * Enables millimeter/centimeter indoor pedestrian dead-reckoning via ARCore camera tracking.
     */
    fun worldToGeo(worldPoint: Vector3): GeoPoint {
        val deltaW = worldPoint - arOriginW
        val east = Vector3.dot(deltaW, eastW).toDouble()
        val north = Vector3.dot(deltaW, northW).toDouble()
        val up = Vector3.dot(deltaW, upW).toDouble()
        return GeoUtils.fromEnu(EnuPoint(eastM = east, northM = north, upM = up), geoOrigin)
    }
}
