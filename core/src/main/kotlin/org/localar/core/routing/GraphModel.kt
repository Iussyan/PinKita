package org.localar.core.routing

import org.localar.core.geo.GeoPoint

/**
 * Pedestrian graph node representing a walkable intersection, transition, or entrance.
 */
data class PedestrianNode(
    val id: Long,
    val point: GeoPoint,
    val layer: Int = 0,
    val componentId: Int = 0,
    val barrier: String? = null
)

/**
 * Directed walkable edge connecting two nodes with audited pedestrian attributes.
 */
data class PedestrianEdge(
    val id: Long,
    val fromNode: Long,
    val toNode: Long,
    val lengthM: Double,
    val costS: Double,
    val polyline: List<GeoPoint>,
    val pathClass: String = "footway",
    val crossingKind: String? = null,
    val stairs: Boolean = false,
    val layer: Int = 0,
    val accessStatus: String = "yes"
)

/**
 * Immutable in-memory pedestrian graph snapshot.
 */
class PedestrianGraph(
    val nodes: Map<Long, PedestrianNode>,
    val edges: List<PedestrianEdge>
) {
    // Fast adjacency lookup: fromNode -> list of outgoing edges
    val outgoingEdges: Map<Long, List<PedestrianEdge>> = edges.groupBy { it.fromNode }

    fun getOutgoing(nodeId: Long): List<PedestrianEdge> = outgoingEdges[nodeId] ?: emptyList()
    fun getNode(nodeId: Long): PedestrianNode? = nodes[nodeId]
}
