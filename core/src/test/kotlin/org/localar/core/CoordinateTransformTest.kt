package org.localar.core

import org.junit.Assert.*
import org.junit.Test
import org.localar.core.geo.*
import kotlin.math.abs

class CoordinateTransformTest {

    private val origin = GeoPoint(lat = 14.5995, lon = 120.9842, altitudeM = 10.0) // Manila reference

    @Test
    fun testEnuRoundTrip() {
        // Point offset ~100m East and ~200m North
        val enuOriginal = EnuPoint(eastM = 100.0, northM = 200.0, upM = 5.0)
        val geo = GeoUtils.fromEnu(enuOriginal, origin)
        val enuCalculated = GeoUtils.toEnu(geo, origin)

        assertEquals(enuOriginal.eastM, enuCalculated.eastM, 1e-3)
        assertEquals(enuOriginal.northM, enuCalculated.northM, 1e-3)
        assertEquals(enuOriginal.upM, enuCalculated.upM, 1e-3)
    }

    @Test
    fun testHaversineDistance() {
        val p1 = GeoPoint(14.5995, 120.9842)
        val p2 = GeoPoint(14.6000, 120.9842) // ~55.6m North
        val dist = GeoUtils.distanceMeters(p1, p2)
        assertTrue("Distance should be approximately 55-56m, was $dist", dist in 54.0..57.0)
    }

    @Test
    fun testCardinalBearings() {
        val center = GeoPoint(14.5995, 120.9842)
        val north = GeoPoint(14.6095, 120.9842)
        val east = GeoPoint(14.5995, 120.9942)
        val south = GeoPoint(14.5895, 120.9842)
        val west = GeoPoint(14.5995, 120.9742)

        assertEquals(0.0, GeoUtils.bearingDegrees(center, north), 0.5)
        assertEquals(90.0, GeoUtils.bearingDegrees(center, east), 0.5)
        assertEquals(180.0, GeoUtils.bearingDegrees(center, south), 0.5)
        assertEquals(270.0, GeoUtils.bearingDegrees(center, west), 0.5)
    }

    @Test
    fun testAngleDeltaDegrees() {
        // Turning from North (0 deg) to East (90 deg) -> +90 deg right
        assertEquals(90.0, GeoUtils.angleDeltaDegrees(90.0, 0.0), 1e-4)

        // Turning from North (0 deg) to West (270 deg) -> -90 deg left
        assertEquals(-90.0, GeoUtils.angleDeltaDegrees(270.0, 0.0), 1e-4)

        // Crossing 0 boundary: 350 deg to 10 deg -> +20 deg right
        assertEquals(20.0, GeoUtils.angleDeltaDegrees(10.0, 350.0), 1e-4)

        // Crossing 0 boundary: 10 deg to 350 deg -> -20 deg left
        assertEquals(-20.0, GeoUtils.angleDeltaDegrees(350.0, 10.0), 1e-4)
    }

    @Test
    fun testArAlignerCardinalOrientations() {
        val arOrigin = Vector3(0f, 0f, 0f)

        // Scenario 1: Camera points North (theta = 0) with camera forward along -Z (standard OpenGL/Filament convention)
        val alignerNorth = ArAligner(
            geoOrigin = origin,
            arOriginW = arOrigin,
            headingThetaRad = 0.0,
            cameraForwardW = Vector3(0f, 0f, -1f)
        )

        // Point 10m North should map towards -Z (camera forward)
        val pointNorth = alignerNorth.enuToWorld(EnuPoint(eastM = 0.0, northM = 10.0))
        assertEquals(0f, pointNorth.x, 1e-3f)
        assertEquals(0f, pointNorth.y, 1e-3f)
        assertEquals(-10f, pointNorth.z, 1e-3f)

        // Point 10m East should map towards +X (camera right)
        val pointEast = alignerNorth.enuToWorld(EnuPoint(eastM = 10.0, northM = 0.0))
        assertEquals(10f, pointEast.x, 1e-3f)
        assertEquals(0f, pointEast.y, 1e-3f)
        assertEquals(0f, pointEast.z, 1e-3f)
    }
}
