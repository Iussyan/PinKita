package org.localar.navigator.vision

import org.json.JSONObject
import org.localar.core.recognition.LandmarkMatcher
import org.localar.core.recognition.ReferenceEmbedding
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PackEmbeddingLoader {

    /**
     * Loads embeddings.f32, embedding_index.json, and thresholds.json from the pack's ai/ directory
     * and constructs an active LandmarkMatcher.
     */
    fun loadMatcher(aiDir: File): LandmarkMatcher? {
        val f32File = File(aiDir, "embeddings.f32")
        val indexFile = File(aiDir, "embedding_index.json")
        val threshFile = File(aiDir, "thresholds.json")

        if (!f32File.exists() || !indexFile.exists() || !threshFile.exists()) {
            return null
        }

        try {
            // 1. Parse embedding_index.json
            val indexJson = JSONObject(indexFile.readText(Charsets.UTF_8))
            val dimension = indexJson.getInt("dimension")
            val rows = indexJson.getJSONArray("rows")

            // 2. Read binary embeddings.f32
            val bytes = f32File.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val references = mutableListOf<ReferenceEmbedding>()
            for (i in 0 until rows.length()) {
                val rowObj = rows.getJSONObject(i)
                val landmarkId = rowObj.getString("landmark_id")
                val refId = rowObj.getString("reference_id")

                val vector = FloatArray(dimension)
                for (d in 0 until dimension) {
                    vector[d] = buffer.float
                }

                references.add(ReferenceEmbedding(landmarkId, refId, vector))
            }

            // 3. Parse thresholds.json
            val threshJson = JSONObject(threshFile.readText(Charsets.UTF_8))
            val defaultThresh = threshJson.optDouble("default_threshold", 0.70).toFloat()
            val calibratedMargin = threshJson.optDouble("calibrated_margin", 0.08).toFloat()

            val landmarkThresholds = mutableMapOf<String, Float>()
            val threshMapObj = threshJson.optJSONObject("landmark_thresholds")
            if (threshMapObj != null) {
                for (key in threshMapObj.keys()) {
                    landmarkThresholds[key] = threshMapObj.getDouble(key).toFloat()
                }
            }

            return LandmarkMatcher(
                references = references,
                defaultThreshold = defaultThresh,
                landmarkThresholds = landmarkThresholds,
                calibratedMargin = calibratedMargin
            )
        } catch (_: Exception) {
            return null
        }
    }
}
