package org.localar.core.routing

import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import kotlin.math.max
import kotlin.math.min

data class SnappedStart(
    val virtualNodeId: Long,
    val snappedPoint: GeoPoint,
    val distanceToEdgeM: Double,
    val modifiedGraph: PedestrianGraph
)

/**
 * Snaps a raw GNSS location fix onto the nearest walkable edge within maxSnapDistanceM.
 * Implements Section 10.4 of spec.md.
 */
object StartSnapper {
    const val DEFAULT_MAX_SNAP_DISTANCE_M = 30.0
    const val VIRTUAL_START_NODE_ID = -1L

    fun snapToGraph(
        userFix: GeoPoint,
        graph: PedestrianGraph,
        maxDistanceM: Double = DEFAULT_MAX_SNAP_DISTANCE_M
    ): SnappedStart? {
        var bestEdge: PedestrianEdge? = null
        var bestPoint: GeoPoint? = null
        var minDistance = Double.MAX_VALUE
        var bestT = 0.0

        for (edge in graph.edges) {
            val poly = edge.polyline
            for (i in 0 until poly.size - 1) {
                val p1 = poly[i]
                val p2 = poly[i + 1]

                val (proj, dist, t) = projectPointToSegment(userFix, p1, p2)
                if (dist < minDistance && dist <= maxDistanceM) {
                    minDistance = dist
                    bestEdge = edge
                    bestPoint = proj
                    bestT = t
                }
            }
        }

        val targetEdge = bestEdge ?: return null
        val targetPoint = bestPoint ?: return null

        // Build a virtual start node and split the target edge into two partial edges
        val virtualNode = PedestrianNode(
            id = VIRTUAL_START_NODE_ID,
            point = targetPoint,
            layer = targetEdge.layer,
            componentId = graph.getNode(targetEdge.fromNode)?.componentId ?: 0
        )

        val newNodes = graph.nodes.toMutableMap()
        newNodes[VIRTUAL_START_NODE_ID] = virtualNode

        // Partial edge: from virtualNode to targetEdge.toNode
        val remainingLength = targetEdge.lengthM * (1.0 - bestT)
        val remainingCost = targetEdge.costS * (1.0 - bestT)

        val virtualEdgeTo = PedestrianEdge(
            id = -100L,
            fromNode = VIRTUAL_START_NODE_ID,
            toNode = targetEdge.toNode,
            lengthM = max(0.1, remainingLength),
            costS = max(0.1, remainingCost),
            polyline = listOf(targetPoint, targetEdge.polyline.last()),
            pathClass = targetEdge.pathClass,
            layer = targetEdge.layer
        )

        val newEdges = graph.edges.toMutableList()
        newEdges.add(virtualEdgeTo)

        val augmentedGraph = PedestrianGraph(newNodes, newEdges)

        return SnappedStart(
            virtualNodeId = VIRTUAL_START_NODE_ID,
            snappedPoint = targetPoint,
            distanceToEdgeM = minDistance,
            modifiedGraph = augmentedGraph
        )
    }

    data class ProjResult(val point: GeoPoint, val distM: Double, val t: Double)

    fun projectPointToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): ProjResult {
        val totalDist = GeoUtils.distanceMeters(a, b)
        if (totalDist < 1e-4) {
            return ProjResult(a, GeoUtils.distanceMeters(p, a), 0.0)
        }

        // Projection using linear approximation
        val dLat = b.lat - a.lat
        val dLon = b.lon - a.lon

        val tRaw = ((p.lat - a.lat) * dLat + (p.lon - a.lon) * dLon) / (dLat * dLat + dLon * dLon)
        val t = max(0.0, min(1.0, tRaw))

        val projLat = a.lat + t * dLat
        val projLon = a.lon + t * dLon
        val projPoint = GeoPoint(projLat, projLon)

        val dist = GeoUtils.distanceMeters(p, projPoint)
        return ProjResult(projPoint, dist, t)
    }
}
