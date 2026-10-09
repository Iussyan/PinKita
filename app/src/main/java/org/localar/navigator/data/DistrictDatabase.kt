package org.localar.navigator.data

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.localar.core.geo.GeoPoint
import org.localar.core.routing.PedestrianEdge
import org.localar.core.routing.PedestrianGraph
import org.localar.core.routing.PedestrianNode
import java.io.File

class DistrictDatabase(private val dbFile: File) : AutoCloseable {

    private val db: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun getMetadata(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cursor = db.rawQuery("SELECT key, value FROM metadata", null)
        cursor.use {
            val keyIdx = cursor.getColumnIndexOrThrow("key")
            val valIdx = cursor.getColumnIndexOrThrow("value")
            while (cursor.moveToNext()) {
                result[cursor.getString(keyIdx)] = cursor.getString(valIdx)
            }
        }
        return result
    }

    fun searchPlaces(query: String): List<Place> {
        val cleanQuery = query.trim().lowercase()
        val results = mutableListOf<Place>()
        val seenIds = mutableSetOf<String>()

        val sql = if (cleanQuery.isEmpty()) {
            "SELECT place_id, osm_ref, name, normalized_name, category, lat, lon, building_id, recognition_supported FROM places ORDER BY name ASC LIMIT 20"
        } else {
            """
            SELECT DISTINCT p.place_id, p.osm_ref, p.name, p.normalized_name, p.category, p.lat, p.lon, p.building_id, p.recognition_supported,
              CASE 
                WHEN p.normalized_name = ? THEN 1
                WHEN p.normalized_name LIKE ? THEN 2
                WHEN a.normalized_alias LIKE ? THEN 3
                ELSE 4
              END as search_rank
            FROM places p
            LEFT JOIN place_aliases a ON p.place_id = a.place_id
            WHERE p.normalized_name LIKE ? OR a.normalized_alias LIKE ?
            ORDER BY search_rank ASC, p.name ASC
            LIMIT 20
            """.trimIndent()
        }

        val args = if (cleanQuery.isEmpty()) {
            null
        } else {
            val exact = cleanQuery
            val prefix = "$cleanQuery%"
            val substring = "%$cleanQuery%"
            arrayOf(exact, prefix, prefix, substring, substring)
        }

        val cursor = db.rawQuery(sql, args)
        cursor.use {
            val idIdx = cursor.getColumnIndexOrThrow("place_id")
            val osmIdx = cursor.getColumnIndexOrThrow("osm_ref")
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val normIdx = cursor.getColumnIndexOrThrow("normalized_name")
            val catIdx = cursor.getColumnIndexOrThrow("category")
            val latIdx = cursor.getColumnIndexOrThrow("lat")
            val lonIdx = cursor.getColumnIndexOrThrow("lon")
            val bldIdx = cursor.getColumnIndexOrThrow("building_id")
            val recIdx = cursor.getColumnIndexOrThrow("recognition_supported")

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)
                if (seenIds.add(id)) {
                    results.add(
                        Place(
                            id = id,
                            osmRef = if (cursor.isNull(osmIdx)) null else cursor.getString(osmIdx),
                            name = cursor.getString(nameIdx),
                            normalizedName = cursor.getString(normIdx),
                            category = cursor.getString(catIdx),
                            lat = cursor.getDouble(latIdx),
                            lon = cursor.getDouble(lonIdx),
                            buildingId = if (cursor.isNull(bldIdx)) null else cursor.getString(bldIdx),
                            recognitionSupported = cursor.getInt(recIdx) == 1
                        )
                    )
                }
            }
        }
        return results
    }

    fun getEntrancesForPlace(placeId: String): List<PlaceEntrance> {
        val results = mutableListOf<PlaceEntrance>()
        val cursor = db.rawQuery(
            "SELECT entrance_id, place_id, name, lat, lon, graph_node_id, access_status, reviewed FROM entrances WHERE place_id = ? ORDER BY reviewed DESC, name ASC",
            arrayOf(placeId)
        )
        cursor.use {
            val idIdx = cursor.getColumnIndexOrThrow("entrance_id")
            val placeIdx = cursor.getColumnIndexOrThrow("place_id")
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val latIdx = cursor.getColumnIndexOrThrow("lat")
            val lonIdx = cursor.getColumnIndexOrThrow("lon")
            val nodeIdx = cursor.getColumnIndexOrThrow("graph_node_id")
            val accessIdx = cursor.getColumnIndexOrThrow("access_status")
            val revIdx = cursor.getColumnIndexOrThrow("reviewed")

            while (cursor.moveToNext()) {
                results.add(
                    PlaceEntrance(
                        id = cursor.getString(idIdx),
                        placeId = cursor.getString(placeIdx),
                        name = if (cursor.isNull(nameIdx)) null else cursor.getString(nameIdx),
                        lat = cursor.getDouble(latIdx),
                        lon = cursor.getDouble(lonIdx),
                        graphNodeId = cursor.getLong(nodeIdx),
                        accessStatus = cursor.getString(accessIdx),
                        reviewed = cursor.getInt(revIdx) == 1
                    )
                )
            }
        }
        return results
    }

    fun loadRoutingGraph(): PedestrianGraph {
        val nodesMap = mutableMapOf<Long, PedestrianNode>()

        val nodeCursor = db.rawQuery("SELECT node_id, lat, lon, layer, component_id, barrier FROM nodes", null)
        nodeCursor.use {
            val idIdx = nodeCursor.getColumnIndexOrThrow("node_id")
            val latIdx = nodeCursor.getColumnIndexOrThrow("lat")
            val lonIdx = nodeCursor.getColumnIndexOrThrow("lon")
            val layerIdx = nodeCursor.getColumnIndexOrThrow("layer")
            val compIdx = nodeCursor.getColumnIndexOrThrow("component_id")
            val barIdx = nodeCursor.getColumnIndexOrThrow("barrier")

            while (nodeCursor.moveToNext()) {
                val nid = nodeCursor.getLong(idIdx)
                nodesMap[nid] = PedestrianNode(
                    id = nid,
                    point = GeoPoint(nodeCursor.getDouble(latIdx), nodeCursor.getDouble(lonIdx)),
                    layer = nodeCursor.getInt(layerIdx),
                    componentId = nodeCursor.getInt(compIdx),
                    barrier = if (nodeCursor.isNull(barIdx)) null else nodeCursor.getString(barIdx)
                )
            }
        }

        val edgesList = mutableListOf<PedestrianEdge>()
        val edgeCursor = db.rawQuery(
            """
            SELECT edge_id, from_node, to_node, length_m, cost_s, geometry_json, 
                   path_class, crossing_kind, access_status, stairs, layer 
            FROM edges
            """.trimIndent(),
            null
        )

        edgeCursor.use {
            val idIdx = edgeCursor.getColumnIndexOrThrow("edge_id")
            val fromIdx = edgeCursor.getColumnIndexOrThrow("from_node")
            val toIdx = edgeCursor.getColumnIndexOrThrow("to_node")
            val lenIdx = edgeCursor.getColumnIndexOrThrow("length_m")
            val costIdx = edgeCursor.getColumnIndexOrThrow("cost_s")
            val geomIdx = edgeCursor.getColumnIndexOrThrow("geometry_json")
            val classIdx = edgeCursor.getColumnIndexOrThrow("path_class")
            val crossIdx = edgeCursor.getColumnIndexOrThrow("crossing_kind")
            val accIdx = edgeCursor.getColumnIndexOrThrow("access_status")
            val stairsIdx = edgeCursor.getColumnIndexOrThrow("stairs")
            val layerIdx = edgeCursor.getColumnIndexOrThrow("layer")

            while (edgeCursor.moveToNext()) {
                val eid = edgeCursor.getLong(idIdx)
                val fromNode = edgeCursor.getLong(fromIdx)
                val toNode = edgeCursor.getLong(toIdx)
                val geomJson = edgeCursor.getString(geomIdx)

                val points = parsePolyline(geomJson, nodesMap[fromNode], nodesMap[toNode])

                edgesList.add(
                    PedestrianEdge(
                        id = eid,
                        fromNode = fromNode,
                        toNode = toNode,
                        lengthM = edgeCursor.getDouble(lenIdx),
                        costS = edgeCursor.getDouble(costIdx),
                        polyline = points,
                        pathClass = edgeCursor.getString(classIdx),
                        crossingKind = if (edgeCursor.isNull(crossIdx)) null else edgeCursor.getString(crossIdx),
                        stairs = edgeCursor.getInt(stairsIdx) == 1,
                        layer = edgeCursor.getInt(layerIdx),
                        accessStatus = edgeCursor.getString(accIdx)
                    )
                )
            }
        }

        return PedestrianGraph(nodes = nodesMap, edges = edgesList)
    }

    fun getAllPathsGeoJson(): String {
        val cursor = db.rawQuery("SELECT geometry_json, stairs, crossing_kind FROM edges", null)
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true

        cursor.use {
            val geomIdx = cursor.getColumnIndexOrThrow("geometry_json")
            val stairsIdx = cursor.getColumnIndexOrThrow("stairs")
            val crossIdx = cursor.getColumnIndexOrThrow("crossing_kind")

            while (cursor.moveToNext()) {
                if (!first) sb.append(",")
                first = false

                val geom = cursor.getString(geomIdx)
                val isStairs = cursor.getInt(stairsIdx) == 1
                val crossing = if (cursor.isNull(crossIdx)) "none" else cursor.getString(crossIdx)

                sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":$geom},\"properties\":{\"stairs\":$isStairs,\"crossing\":\"$crossing\"}}")
            }
        }

        sb.append("]}")
        return sb.toString()
    }

    private fun parsePolyline(
        jsonString: String,
        fromNode: PedestrianNode?,
        toNode: PedestrianNode?
    ): List<GeoPoint> {
        return try {
            val arr = JSONArray(jsonString)
            val list = mutableListOf<GeoPoint>()
            for (i in 0 until arr.length()) {
                val coord = arr.getJSONArray(i)
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                list.add(GeoPoint(lat = lat, lon = lon))
            }
            if (list.size >= 2) list else fallbackPolyline(fromNode, toNode)
        } catch (_: Exception) {
            fallbackPolyline(fromNode, toNode)
        }
    }

    private fun fallbackPolyline(fromNode: PedestrianNode?, toNode: PedestrianNode?): List<GeoPoint> {
        return if (fromNode != null && toNode != null) {
            listOf(fromNode.point, toNode.point)
        } else {
            emptyList()
        }
    }

    override fun close() {
        if (db.isOpen) {
            db.close()
        }
    }
}
