package org.localar.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.localar.core.geo.GeoPoint
import org.localar.core.routing.*

class AStarRoutingTest {

    private lateinit var graph: PedestrianGraph
    private val n1 = PedestrianNode(1L, GeoPoint(14.5990, 120.9840))
    private val n2 = PedestrianNode(2L, GeoPoint(14.5995, 120.9840))
    private val n3 = PedestrianNode(3L, GeoPoint(14.5995, 120.9850))
    private val n4 = PedestrianNode(4L, GeoPoint(14.6000, 120.9850))
    private val n5 = PedestrianNode(5L, GeoPoint(14.5985, 120.9840))
    private val n6 = PedestrianNode(6L, GeoPoint(14.5985, 120.9870))
    private val n7Disconnected = PedestrianNode(7L, GeoPoint(14.6100, 120.9900), componentId = 2)

    @Before
    fun setup() {
        val nodes = listOf(n1, n2, n3, n4, n5, n6, n7Disconnected).associateBy { it.id }

        val edges = listOf(
            // 1 -> 2 (Footway, ~55m)
            PedestrianEdge(
                id = 101L,
                fromNode = 1L,
                toNode = 2L,
                lengthM = 55.0,
                costS = 55.0 / 1.3,
                polyline = listOf(n1.point, n2.point),
                pathClass = "footway"
            ),
            // 2 -> 3 (Crossing, ~108m)
            PedestrianEdge(
                id = 102L,
                fromNode = 2L,
                toNode = 3L,
                lengthM = 108.0,
                costS = (108.0 / 1.3) + 15.0, // 15s crossing penalty
                polyline = listOf(n2.point, n3.point),
                pathClass = "footway",
                crossingKind = "zebra"
            ),
            // 3 -> 4 (Stairs, ~55m)
            PedestrianEdge(
                id = 103L,
                fromNode = 3L,
                toNode = 4L,
                lengthM = 55.0,
                costS = (55.0 / 1.3) + 20.0, // 20s stairs penalty
                polyline = listOf(n3.point, n4.point),
                stairs = true
            ),
            // 1 -> 5 (Footway)
            PedestrianEdge(
                id = 104L,
                fromNode = 1L,
                toNode = 5L,
                lengthM = 55.0,
                costS = 55.0 / 1.3,
                polyline = listOf(n1.point, n5.point)
            ),
            // 5 -> 6 (Long path, ~320m)
            PedestrianEdge(
                id = 105L,
                fromNode = 5L,
                toNode = 6L,
                lengthM = 320.0,
                costS = 320.0 / 1.3,
                polyline = listOf(n5.point, n6.point)
            )
        )

        graph = PedestrianGraph(nodes, edges)
    }

    @Test
    fun testShortestPathFound() {
        val router = AStarRouter(graph)
        val result = router.calculateRoute(startNodeId = 1L, goalNodeId = 4L)

        assertTrue("Expected route to succeed", result is RouteResult.Success)
        val plan = (result as RouteResult.Success).plan

        assertEquals(listOf(1L, 2L, 3L, 4L), plan.orderedNodes)
        assertEquals(3, plan.orderedEdges.size)
        assertTrue(plan.totalDistanceM in 215.0..220.0)

        // Verify maneuvers contain crossing and stairs
        val maneuverTypes = plan.maneuvers.map { it.type }
        assertTrue("Maneuvers must include CROSSING", maneuverTypes.contains(ManeuverType.CROSSING))
        assertTrue("Maneuvers must include STAIRS", maneuverTypes.contains(ManeuverType.STAIRS))
        assertEquals(ManeuverType.ARRIVE, plan.maneuvers.last().type)
    }

    @Test
    fun testDisconnectedComponentReturnsNoPath() {
        val router = AStarRouter(graph)
        val result = router.calculateRoute(startNodeId = 1L, goalNodeId = 7L)

        assertEquals(RouteResult.NoPath, result)
    }

    @Test
    fun testStartSnappingAndRouting() {
        // User fix 10m east of midpoint between node 1 and 2
        val userFix = GeoPoint(14.59925, 120.9841)
        val snap = StartSnapper.snapToGraph(userFix, graph, maxDistanceM = 30.0)

        assertNotNull("Should snap successfully within 30m", snap)
        val router = AStarRouter(snap!!.modifiedGraph)
        val result = router.calculateRoute(startNodeId = snap.virtualNodeId, goalNodeId = 4L)

        assertTrue("Route from snapped virtual start should succeed", result is RouteResult.Success)
        val plan = (result as RouteResult.Success).plan
        assertEquals(snap.virtualNodeId, plan.orderedNodes.first())
        assertEquals(4L, plan.orderedNodes.last())
    }
}
