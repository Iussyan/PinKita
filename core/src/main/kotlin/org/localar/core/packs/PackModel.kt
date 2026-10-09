package org.localar.core.packs

import org.json.JSONObject
import org.localar.core.geo.GeoPoint

data class PackBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double
) {
    fun contains(point: GeoPoint): Boolean {
        return point.lon in west..east && point.lat in south..north
    }
}

data class PackAiMetadata(
    val modelId: String,
    val modelPath: String,
    val modelSha256: String,
    val embeddingDimension: Int,
    val preprocessId: String,
    val embeddingFormat: String,
    val calibrationId: String
)

data class PackFileEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String
)

data class PackAttribution(
    val label: String,
    val url: String
)

data class PackManifest(
    val schemaVersion: Int,
    val regionId: String,
    val displayName: String,
    val packVersion: String,
    val builtAt: String,
    val bounds: PackBounds,
    val routingProfile: String,
    val ai: PackAiMetadata?,
    val files: List<PackFileEntry>,
    val attributions: List<PackAttribution>
) {
    companion object {
        fun parse(jsonStr: String): PackManifest {
            val json = JSONObject(jsonStr)

            val schemaVersion = json.getInt("schema_version")
            val regionId = json.getString("region_id")
            val displayName = json.getString("display_name")
            val packVersion = json.getString("pack_version")
            val builtAt = json.optString("built_at", "")
            val routingProfile = json.optString("routing_profile", "pedestrian-v1")

            val boundsObj = json.getJSONObject("bounds")
            val bounds = PackBounds(
                west = boundsObj.getDouble("west"),
                south = boundsObj.getDouble("south"),
                east = boundsObj.getDouble("east"),
                north = boundsObj.getDouble("north")
            )

            val aiObj = json.optJSONObject("ai")
            val ai = if (aiObj != null) {
                PackAiMetadata(
                    modelId = aiObj.getString("model_id"),
                    modelPath = aiObj.getString("model_path"),
                    modelSha256 = aiObj.getString("model_sha256"),
                    embeddingDimension = aiObj.getInt("embedding_dimension"),
                    preprocessId = aiObj.getString("preprocess_id"),
                    embeddingFormat = aiObj.getString("embedding_format"),
                    calibrationId = aiObj.getString("calibration_id")
                )
            } else null

            val filesArray = json.optJSONArray("files")
            val files = mutableListOf<PackFileEntry>()
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val f = filesArray.getJSONObject(i)
                    files.add(
                        PackFileEntry(
                            path = f.getString("path"),
                            sizeBytes = f.getLong("size_bytes"),
                            sha256 = f.getString("sha256")
                        )
                    )
                }
            }

            val attrArray = json.optJSONArray("attributions")
            val attributions = mutableListOf<PackAttribution>()
            if (attrArray != null) {
                for (i in 0 until attrArray.length()) {
                    val a = attrArray.getJSONObject(i)
                    attributions.add(
                        PackAttribution(
                            label = a.getString("label"),
                            url = a.getString("url")
                        )
                    )
                }
            }

            return PackManifest(
                schemaVersion = schemaVersion,
                regionId = regionId,
                displayName = displayName,
                packVersion = packVersion,
                builtAt = builtAt,
                bounds = bounds,
                routingProfile = routingProfile,
                ai = ai,
                files = files,
                attributions = attributions
            )
        }
    }
}
