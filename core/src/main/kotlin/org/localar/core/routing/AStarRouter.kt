package org.localar.core.routing

import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import java.util.PriorityQueue
import kotlin.math.abs

enum class ManeuverType {
    DEPART,
    CONTINUE,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    LEFT,
    RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    STAIRS,
    CROSSING,
    ARRIVE
}

data class Maneuver(
    val type: ManeuverType,
    val instruction: String,
    val point: GeoPoint,
    val distanceToNextM: Double = 0.0
)

data class RoutePlan(
    val routeId: String,
    val totalDistanceM: Double,
    val totalCostS: Double,
    val orderedNodes: List<Long>,
    val orderedEdges: List<PedestrianEdge>,
    val polyline: List<GeoPoint>,
    val maneuvers: List<Maneuver>
)

sealed class RouteResult {
    data class Success(val plan: RoutePlan) : RouteResult()
    object NoPath : RouteResult()
    data class StartNotRoutable(val message: String) : RouteResult()
    data class DestinationNotRoutable(val message: String) : RouteResult()
    object Cancelled : RouteResult()
}

/**
 * Pure Kotlin A* Pedestrian Router.
 * Implements Sections 10 and 11 of spec.md.
 */
class AStarRouter(
    private val graph: PedestrianGraph,
    private val walkSpeedMps: Double = 1.3
) {
    private data class QueueNode(
        val nodeId: Long,
        val fScore: Double,
        val gScore: Double
    ) : Comparable<QueueNode> {
        override fun compareTo(other: QueueNode): Int = fScore.compareTo(other.fScore)
    }

    /**
     * Calculates optimal pedestrian route from startNodeId to goalNodeId.
     */
    fun calculateRoute(startNodeId: Long, goalNodeId: Long): RouteResult {
        val startNode = graph.getNode(startNodeId) ?: return RouteResult.StartNotRoutable("Start node not found")
        val goalNode = graph.getNode(goalNodeId) ?: return RouteResult.DestinationNotRoutable("Goal node not found")

        if (startNodeId == goalNodeId) {
            val maneuver = Maneuver(ManeuverType.ARRIVE, "You are already at your destination.", startNode.point)
            return RouteResult.Success(
                RoutePlan(
                    routeId = "same-node",
                    totalDistanceM = 0.0,
                    totalCostS = 0.0,
                    orderedNodes = listOf(startNodeId),
                    orderedEdges = emptyList(),
                    polyline = listOf(startNode.point),
                    maneuvers = listOf(maneuver)
                )
            )
        }

        // gScore map: cost from start to node
        val gScore = mutableMapOf<Long, Double>()
        // Predecessor map: node -> incoming edge
        val cameFrom = mutableMapOf<Long, PedestrianEdge>()

        val openSet = PriorityQueue<QueueNode>()

        gScore[startNodeId] = 0.0
        val initialH = heuristic(startNode.point, goalNode.point)
        openSet.add(QueueNode(startNodeId, fScore = initialH, gScore = 0.0))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break

            // If we found a stale queue entry, skip it
            if (current.gScore > (gScore[current.nodeId] ?: Double.MAX_VALUE)) {
                continue
            }

            if (current.nodeId == goalNodeId) {
                return reconstructRoute(startNodeId, goalNodeId, cameFrom)
            }

            for (edge in graph.getOutgoing(current.nodeId)) {
                val tentativeGScore = current.gScore + edge.costS
                val neighborId = edge.toNode

                if (tentativeGScore < (gScore[neighborId] ?: Double.MAX_VALUE)) {
                    cameFrom[neighborId] = edge
                    gScore[neighborId] = tentativeGScore
                    val neighborNode = graph.getNode(neighborId) ?: continue
                    val fScore = tentativeGScore + heuristic(neighborNode.point, goalNode.point)
                    openSet.add(QueueNode(neighborId, fScore, tentativeGScore))
                }
            }
        }

        return RouteResult.NoPath
    }

    /**
     * Admissible Euclidean geodesic distance heuristic: h(n) = distance(n, goal) / walkSpeedMps
     */
    private fun heuristic(current: GeoPoint, goal: GeoPoint): Double {
        return GeoUtils.distanceMeters(current, goal) / walkSpeedMps
    }

    private fun reconstructRoute(
        startNodeId: Long,
        goalNodeId: Long,
        cameFrom: Map<Long, PedestrianEdge>
    ): RouteResult {
        val edges = mutableListOf<PedestrianEdge>()
        var curr = goalNodeId

        while (curr != startNodeId) {
            val edge = cameFrom[curr] ?: return RouteResult.NoPath
            edges.add(edge)
            curr = edge.fromNode
        }

        edges.reverse()

        val orderedNodes = mutableListOf<Long>()
        orderedNodes.add(startNodeId)
        for (edge in edges) {
            orderedNodes.add(edge.toNode)
        }

        val polyline = mutableListOf<GeoPoint>()
        var totalDistanceM = 0.0
        var totalCostS = 0.0

        for (edge in edges) {
            totalDistanceM += edge.lengthM
            totalCostS += edge.costS
            if (polyline.isEmpty()) {
                polyline.addAll(edge.polyline)
            } else {
                // Avoid duplicate points at segment junctions
                if (edge.polyline.isNotEmpty()) {
                    polyline.addAll(edge.polyline.drop(1))
                }
            }
        }

        val maneuvers = buildManeuvers(edges)

        return RouteResult.Success(
            RoutePlan(
                routeId = "route-${startNodeId}-${goalNodeId}",
                totalDistanceM = totalDistanceM,
                totalCostS = totalCostS,
                orderedNodes = orderedNodes,
                orderedEdges = edges,
                polyline = polyline,
                maneuvers = maneuvers
            )
        )
    }

    /**
     * Builds maneuver instructions based on bearings, stairs, and crossings (Section 11.2 of spec.md).
     */
    private fun buildManeuvers(edges: List<PedestrianEdge>): List<Maneuver> {
        if (edges.isEmpty()) return emptyList()

        val maneuvers = mutableListOf<Maneuver>()
        val startEdge = edges.first()
        val startNode = graph.getNode(startEdge.fromNode) ?: return emptyList()

        maneuvers.add(
            Maneuver(
                type = ManeuverType.DEPART,
                instruction = "Head along the path",
                point = startNode.point,
                distanceToNextM = startEdge.lengthM
            )
        )

        for (i in 0 until edges.size - 1) {
            val e1 = edges[i]
            val e2 = edges[i + 1]
            val transitionNode = graph.getNode(e1.toNode) ?: continue

            // Check for special path attributes first
            if (e2.stairs) {
                maneuvers.add(
                    Maneuver(
                        type = ManeuverType.STAIRS,
                        instruction = "Use the stairs",
                        point = transitionNode.point,
                        distanceToNextM = e2.lengthM
                    )
                )
                continue
            }

            if (e2.crossingKind != null) {
                maneuvers.add(
                    Maneuver(
                        type = ManeuverType.CROSSING,
                        instruction = "Cross the street via the pedestrian crossing",
                        point = transitionNode.point,
                        distanceToNextM = e2.lengthM
                    )
                )
                continue
            }

            // Compute turn angles
            val b1 = calculateEdgeBearing(e1)
            val b2 = calculateEdgeBearing(e2)
            val delta = GeoUtils.angleDeltaDegrees(b2, b1)

            val (type, text) = when {
                abs(delta) < 25.0 -> ManeuverType.CONTINUE to "Continue straight"
                delta in 25.0..60.0 -> ManeuverType.SLIGHT_RIGHT to "Slight right"
                delta in 60.0..135.0 -> ManeuverType.RIGHT to "Turn right"
                delta > 135.0 -> ManeuverType.SHARP_RIGHT to "Sharp right"
                delta in -60.0..-25.0 -> ManeuverType.SLIGHT_LEFT to "Slight left"
                delta in -135.0..-60.0 -> ManeuverType.LEFT to "Turn left"
                else -> ManeuverType.SHARP_LEFT to "Sharp left"
            }

            if (type != ManeuverType.CONTINUE) {
                maneuvers.add(
                    Maneuver(
                        type = type,
                        instruction = text,
                        point = transitionNode.point,
                        distanceToNextM = e2.lengthM
                    )
                )
            }
        }

        val lastEdge = edges.last()
        val destNode = graph.getNode(lastEdge.toNode)
        if (destNode != null) {
            maneuvers.add(
                Maneuver(
                    type = ManeuverType.ARRIVE,
                    instruction = "You have arrived at your destination",
                    point = destNode.point,
                    distanceToNextM = 0.0
                )
            )
        }

        return maneuvers
    }

    private fun calculateEdgeBearing(edge: PedestrianEdge): Double {
        if (edge.polyline.size >= 2) {
            return GeoUtils.bearingDegrees(edge.polyline.first(), edge.polyline.last())
        }
        val from = graph.getNode(edge.fromNode)?.point ?: return 0.0
        val to = graph.getNode(edge.toNode)?.point ?: return 0.0
        return GeoUtils.bearingDegrees(from, to)
    }
}
