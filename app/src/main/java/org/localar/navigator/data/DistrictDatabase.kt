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

    private fun resolvePlaceIdColumn(cursor: android.database.Cursor): Int {
        val idx = cursor.getColumnIndex("place_id")
        return if (idx >= 0) idx else cursor.getColumnIndexOrThrow("id")
    }

    private fun parsePlace(cursor: android.database.Cursor): Place {
        val idIdx = resolvePlaceIdColumn(cursor)
        val osmIdx = cursor.getColumnIndex("osm_ref")
        val nameIdx = cursor.getColumnIndexOrThrow("name")
        val normIdx = cursor.getColumnIndex("normalized_name")
        val catIdx = cursor.getColumnIndexOrThrow("category")
        val latIdx = cursor.getColumnIndexOrThrow("lat")
        val lonIdx = cursor.getColumnIndexOrThrow("lon")
        val bldIdx = cursor.getColumnIndex("building_id")
        val recIdx = cursor.getColumnIndex("recognition_supported")
        val addrIdx = cursor.getColumnIndex("address")
        val hoursIdx = cursor.getColumnIndex("opening_hours")

        val name = cursor.getString(nameIdx)
        val cat = cursor.getString(catIdx)
        val lat = cursor.getDouble(latIdx)
        val lon = cursor.getDouble(lonIdx)

        return Place(
            id = cursor.getString(idIdx),
            osmRef = if (osmIdx >= 0 && !cursor.isNull(osmIdx)) cursor.getString(osmIdx) else null,
            name = name,
            normalizedName = if (normIdx >= 0 && !cursor.isNull(normIdx)) cursor.getString(normIdx) else name.lowercase(),
            category = cat,
            lat = lat,
            lon = lon,
            buildingId = if (bldIdx >= 0 && !cursor.isNull(bldIdx)) cursor.getString(bldIdx) else null,
            recognitionSupported = if (recIdx >= 0 && !cursor.isNull(recIdx)) cursor.getInt(recIdx) == 1 else true,
            address = if (addrIdx >= 0 && !cursor.isNull(addrIdx)) cursor.getString(addrIdx) else null,
            openingHours = if (hoursIdx >= 0 && !cursor.isNull(hoursIdx)) cursor.getString(hoursIdx) else null,
            city = "Quezon City",
            tagFeature = when (cat.lowercase()) {
                "cafe", "coffee" -> "Local café"
                "park", "leisure" -> "Scenic greenery"
                "museum", "landmark", "historic" -> "Heritage site"
                "viewpoint" -> "Scenic viewpoint"
                "fast_food", "restaurant" -> "Dining"
                "convenience", "shop", "supermarket" -> "Market & goods"
                else -> "Local destination"
            }
        )
    }

    fun getAllPlaces(limit: Int = 150): List<Place> {
        val results = mutableListOf<Place>()
        val hasPlaceAliases = tableExists("place_aliases")
        val idCol = if (hasColumn("places", "place_id")) "place_id" else "id"
        val cursor = db.rawQuery("SELECT * FROM places ORDER BY name ASC LIMIT $limit", null)
        cursor.use {
            while (cursor.moveToNext()) {
                results.add(parsePlace(cursor))
            }
        }
        return results
    }

    fun getPlaceById(id: String): Place? {
        val idCol = if (hasColumn("places", "place_id")) "place_id" else "id"
        val cursor = db.rawQuery("SELECT * FROM places WHERE $idCol = ? LIMIT 1", arrayOf(id))
        cursor.use {
            if (cursor.moveToNext()) {
                return parsePlace(cursor)
            }
        }
        return null
    }

    fun getPlacesInBounds(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Place> {
        val results = mutableListOf<Place>()
        val cursor = db.rawQuery(
            "SELECT * FROM places WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? ORDER BY name ASC LIMIT 100",
            arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
        )
        cursor.use {
            while (cursor.moveToNext()) {
                results.add(parsePlace(cursor))
            }
        }
        return results
    }

    fun searchPlaces(query: String, categoryFilter: String? = null): List<Place> {
        val cleanQuery = query.trim().lowercase()
        val results = mutableListOf<Place>()
        val seenIds = mutableSetOf<String>()
        val hasAliases = tableExists("place_aliases")
        val idCol = if (hasColumn("places", "place_id")) "place_id" else "id"

        val baseQuery = when {
            cleanQuery.isEmpty() && categoryFilter.isNullOrBlank() -> {
                "SELECT * FROM places ORDER BY name ASC LIMIT 30"
            }
            cleanQuery.isEmpty() && !categoryFilter.isNullOrBlank() -> {
                "SELECT * FROM places WHERE category LIKE ? ORDER BY name ASC LIMIT 30"
            }
            hasAliases -> {
                """
                SELECT DISTINCT p.*,
                  CASE 
                    WHEN p.normalized_name = ? THEN 1
                    WHEN p.normalized_name LIKE ? THEN 2
                    WHEN a.normalized_alias LIKE ? THEN 3
                    ELSE 4
                  END as search_rank
                FROM places p
                LEFT JOIN place_aliases a ON p.$idCol = a.place_id
                WHERE (p.normalized_name LIKE ? OR a.normalized_alias LIKE ?)
                  ${if (!categoryFilter.isNullOrBlank()) "AND p.category LIKE ?" else ""}
                ORDER BY search_rank ASC, p.name ASC
                LIMIT 30
                """.trimIndent()
            }
            else -> {
                """
                SELECT p.*,
                  CASE 
                    WHEN p.normalized_name = ? THEN 1
                    WHEN p.normalized_name LIKE ? THEN 2
                    ELSE 3
                  END as search_rank
                FROM places p
                WHERE (p.normalized_name LIKE ?)
                  ${if (!categoryFilter.isNullOrBlank()) "AND p.category LIKE ?" else ""}
                ORDER BY search_rank ASC, p.name ASC
                LIMIT 30
                """.trimIndent()
            }
        }

        val args: Array<String>? = when {
            cleanQuery.isEmpty() && categoryFilter.isNullOrBlank() -> null
            cleanQuery.isEmpty() && !categoryFilter.isNullOrBlank() -> arrayOf("%$categoryFilter%")
            hasAliases -> {
                val list = mutableListOf(cleanQuery, "$cleanQuery%", "$cleanQuery%", "%$cleanQuery%", "%$cleanQuery%")
                if (!categoryFilter.isNullOrBlank()) list.add("%$categoryFilter%")
                list.toTypedArray()
            }
            else -> {
                val list = mutableListOf(cleanQuery, "$cleanQuery%", "%$cleanQuery%")
                if (!categoryFilter.isNullOrBlank()) list.add("%$categoryFilter%")
                list.toTypedArray()
            }
        }

        val cursor = db.rawQuery(baseQuery, args)
        cursor.use {
            while (cursor.moveToNext()) {
                val place = parsePlace(cursor)
                if (seenIds.add(place.id)) {
                    results.add(place)
                }
            }
        }
        return results
    }

    private fun tableExists(tableName: String): Boolean {
        return try {
            val c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
            c.use { it.moveToFirst() }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasColumn(tableName: String, columnName: String): Boolean {
        return try {
            val c = db.rawQuery("PRAGMA table_info($tableName)", null)
            c.use {
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun getEntrancesForPlace(placeId: String): List<PlaceEntrance> {
        val results = mutableListOf<PlaceEntrance>()
        val idCol = if (hasColumn("entrances", "entrance_id")) "entrance_id" else "id"
        val cursor = db.rawQuery(
            "SELECT * FROM entrances WHERE place_id = ?",
            arrayOf(placeId)
        )
        cursor.use {
            val idIdx = cursor.getColumnIndex("entrance_id").takeIf { it >= 0 } ?: cursor.getColumnIndex("id") ?: -1
            val placeIdx = cursor.getColumnIndex("place_id")
            val nameIdx = cursor.getColumnIndex("name")
            val latIdx = cursor.getColumnIndex("lat")
            val lonIdx = cursor.getColumnIndex("lon")
            val nodeIdx = cursor.getColumnIndex("graph_node_id")
            val accessIdx = cursor.getColumnIndex("access_status")
            val revIdx = cursor.getColumnIndex("reviewed")

            while (cursor.moveToNext()) {
                val eId = if (idIdx >= 0) cursor.getString(idIdx) else "ent-$placeId"
                val pId = if (placeIdx >= 0) cursor.getString(placeIdx) else placeId
                val lat = if (latIdx >= 0) cursor.getDouble(latIdx) else 0.0
                val lon = if (lonIdx >= 0) cursor.getDouble(lonIdx) else 0.0
                val nodeId = if (nodeIdx >= 0) cursor.getLong(nodeIdx) else -1L
                val access = if (accessIdx >= 0 && !cursor.isNull(accessIdx)) cursor.getString(accessIdx) else "public"
                val reviewed = if (revIdx >= 0) cursor.getInt(revIdx) == 1 else true
                val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else "Main Entrance"

                results.add(
                    PlaceEntrance(
                        id = eId,
                        placeId = pId,
                        name = name,
                        lat = lat,
                        lon = lon,
                        graphNodeId = nodeId,
                        accessStatus = access,
                        reviewed = reviewed
                    )
                )
            }
        }
        return results
    }

data class LandmarkRecord(
    val id: String,
    val placeId: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val recognitionRadiusM: Double
)

    fun getLandmarkById(landmarkId: String): LandmarkRecord? {
        val table = when {
            tableExists("landmarks") -> "landmarks"
            tableExists("landmark_records") -> "landmark_records"
            else -> null
        }

        if (table != null) {
            val idCol = if (hasColumn(table, "landmark_id")) "landmark_id" else "id"
            val cursor = db.rawQuery("SELECT * FROM $table WHERE $idCol = ? LIMIT 1", arrayOf(landmarkId))
            cursor.use {
                if (it.moveToNext()) {
                    val nameIdx = if (hasColumn(table, "display_name")) it.getColumnIndex("display_name") else it.getColumnIndex("name")
                    val latIdx = it.getColumnIndexOrThrow("lat")
                    val lonIdx = it.getColumnIndexOrThrow("lon")
                    val placeIdx = if (hasColumn(table, "place_id")) it.getColumnIndex("place_id") else -1
                    val radIdx = if (hasColumn(table, "recognition_radius_m")) it.getColumnIndex("recognition_radius_m") else -1

                    return LandmarkRecord(
                        id = landmarkId,
                        placeId = if (placeIdx >= 0 && !it.isNull(placeIdx)) it.getString(placeIdx) else "",
                        displayName = if (nameIdx >= 0 && !it.isNull(nameIdx)) it.getString(nameIdx) else landmarkId,
                        lat = it.getDouble(latIdx),
                        lon = it.getDouble(lonIdx),
                        recognitionRadiusM = if (radIdx >= 0 && !it.isNull(radIdx)) it.getDouble(radIdx) else 50.0
                    )
                }
            }
        }

        // Fallback: match by place ID or normalized name
        val cleanId = landmarkId.removePrefix("landmark-")
        val p = getPlaceById(cleanId) ?: getPlaceById(landmarkId) ?: searchPlaces(cleanId.replace("-", " ")).firstOrNull()
        if (p != null) {
            return LandmarkRecord(
                id = landmarkId,
                placeId = p.id,
                displayName = p.name,
                lat = p.lat,
                lon = p.lon,
                recognitionRadiusM = 40.0
            )
        }
        return null
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
