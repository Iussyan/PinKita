package org.localar.core.recognition

import kotlin.math.sqrt

enum class RecognitionStatus {
    IDLE,
    SCANNING,
    UNKNOWN,
    AMBIGUOUS,
    POSSIBLE,
    IDENTIFIED,
    EXPIRED
}

data class ReferenceEmbedding(
    val landmarkId: String,
    val referenceId: String,
    val vector: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceEmbedding) return false
        return landmarkId == other.landmarkId && referenceId == other.referenceId
    }

    override fun hashCode(): Int = 31 * landmarkId.hashCode() + referenceId.hashCode()
}

data class LandmarkDecision(
    val status: RecognitionStatus,
    val recognizedLandmarkId: String?,
    val bestScore: Float,
    val margin: Float,
    val processedAtMs: Long
)

/**
 * Evaluates camera embedding vectors against enrolled reference embeddings.
 * Implements Section 15.6 of spec.md.
 */
class LandmarkMatcher(
    private val references: List<ReferenceEmbedding>,
    private val defaultThreshold: Float = 0.70f,
    private val landmarkThresholds: Map<String, Float> = emptyMap(),
    private val calibratedMargin: Float = 0.08f,
    private val temporalWindowSize: Int = 5,
    private val minQualifyingFrames: Int = 3,
    private val minSpanDurationMs: Long = 1000L,
    private val expiryDurationMs: Long = 3000L
) {
    private val history = ArrayDeque<SingleFrameResult>()

    private data class SingleFrameResult(
        val qualifiedLandmarkId: String?,
        val score: Float,
        val margin: Float,
        val timestampMs: Long
    )

    /**
     * Dot product between two L2-normalized float vectors.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        require(v1.size == v2.size) { "Embedding dimensions must match: ${v1.size} vs ${v2.size}" }
        var dot = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot
    }

    /**
     * Evaluates a single captured camera frame embedding.
     */
    fun processFrame(
        queryVector: FloatArray,
        timestampMs: Long,
        candidateLandmarkIds: Set<String>? = null
    ): LandmarkDecision {
        // Prune expired frames from history (> 3000 ms old)
        while (history.isNotEmpty() && timestampMs - history.first().timestampMs > expiryDurationMs) {
            history.removeFirst()
        }

        val filteredRefs = if (candidateLandmarkIds != null) {
            references.filter { it.landmarkId in candidateLandmarkIds }
        } else {
            references
        }

        if (filteredRefs.isEmpty()) {
            return LandmarkDecision(
                status = RecognitionStatus.UNKNOWN,
                recognizedLandmarkId = null,
                bestScore = 0f,
                margin = 0f,
                processedAtMs = timestampMs
            )
        }

        // Group similarity scores by landmark
        val scoresByLandmark = filteredRefs.groupBy { it.landmarkId }
            .mapValues { (_, refs) ->
                val sims = refs.map { cosineSimilarity(queryVector, it.vector) }.sortedDescending()
                if (sims.size >= 2) {
                    (sims[0] + sims[1]) / 2f
                } else {
                    sims.firstOrNull() ?: 0f
                }
            }

        val sortedScores = scoresByLandmark.entries.sortedByDescending { it.value }
        val best = sortedScores.first()
        val second = sortedScores.getOrNull(1)

        val bestLandmarkId = best.key
        val bestScore = best.value
        val secondScore = second?.value ?: 0f
        val margin = if (second != null) bestScore - secondScore else Float.MAX_VALUE

        val threshold = landmarkThresholds[bestLandmarkId] ?: defaultThreshold

        val singleFrameQualified = if (bestScore >= threshold) {
            if (sortedScores.size == 1 || margin >= calibratedMargin) {
                bestLandmarkId
            } else {
                null // Ambiguous: margin too small
            }
        } else {
            null // Below recognition threshold
        }

        history.addLast(
            SingleFrameResult(
                qualifiedLandmarkId = singleFrameQualified,
                score = bestScore,
                margin = margin,
                timestampMs = timestampMs
            )
        )
        if (history.size > temporalWindowSize) {
            history.removeFirst()
        }

        // Temporal rule: landmark qualifies in >= 3 of last 5 frames spanning >= 1 second
        val counts = history.mapNotNull { it.qualifiedLandmarkId }.groupingBy { it }.eachCount()
        val topCandidate = counts.maxByOrNull { it.value }

        if (topCandidate != null && topCandidate.value >= minQualifyingFrames) {
            val qualifyingTimestamps = history
                .filter { it.qualifiedLandmarkId == topCandidate.key }
                .map { it.timestampMs }

            val span = (qualifyingTimestamps.maxOrNull() ?: 0L) - (qualifyingTimestamps.minOrNull() ?: 0L)
            if (span >= minSpanDurationMs) {
                return LandmarkDecision(
                    status = RecognitionStatus.IDENTIFIED,
                    recognizedLandmarkId = topCandidate.key,
                    bestScore = bestScore,
                    margin = margin,
                    processedAtMs = timestampMs
                )
            } else {
                return LandmarkDecision(
                    status = RecognitionStatus.POSSIBLE,
                    recognizedLandmarkId = topCandidate.key,
                    bestScore = bestScore,
                    margin = margin,
                    processedAtMs = timestampMs
                )
            }
        }

        val status = when {
            bestScore < threshold -> RecognitionStatus.UNKNOWN
            second != null && margin < calibratedMargin -> RecognitionStatus.AMBIGUOUS
            else -> RecognitionStatus.POSSIBLE
        }

        return LandmarkDecision(
            status = status,
            recognizedLandmarkId = if (status == RecognitionStatus.POSSIBLE) bestLandmarkId else null,
            bestScore = bestScore,
            margin = margin,
            processedAtMs = timestampMs
        )
    }

    fun reset() {
        history.clear()
    }
}
