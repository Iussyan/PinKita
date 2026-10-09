package org.localar.core

import org.junit.Assert.*
import org.junit.Test
import org.localar.core.recognition.*

class LandmarkMatcherTest {

    private val dim = 4

    // Normalized synthetic embeddings
    private val vA1 = floatArrayOf(1f, 0f, 0f, 0f)
    private val vA2 = floatArrayOf(0.98f, 0.20f, 0f, 0f).apply { normalize(this) }
    private val vB1 = floatArrayOf(0f, 1f, 0f, 0f)
    private val vB2 = floatArrayOf(0f, 0.98f, 0.20f, 0f).apply { normalize(this) }

    private fun normalize(v: FloatArray) {
        var sumSq = 0f
        for (f in v) sumSq += f * f
        val norm = kotlin.math.sqrt(sumSq)
        for (i in v.indices) v[i] /= norm
    }

    private val references = listOf(
        ReferenceEmbedding("landmark-a", "ref-a1", vA1),
        ReferenceEmbedding("landmark-a", "ref-a2", vA2),
        ReferenceEmbedding("landmark-b", "ref-b1", vB1),
        ReferenceEmbedding("landmark-b", "ref-b2", vB2)
    )

    @Test
    fun testCosineSimilarity() {
        val matcher = LandmarkMatcher(references)
        assertEquals(1.0f, matcher.cosineSimilarity(vA1, vA1), 1e-4f)
        assertEquals(0.0f, matcher.cosineSimilarity(vA1, vB1), 1e-4f)
    }

    @Test
    fun testUnknownRejection() {
        val matcher = LandmarkMatcher(references, defaultThreshold = 0.70f)
        // Orthogonal vector (e.g. [0, 0, 1, 0])
        val unknownQuery = floatArrayOf(0f, 0f, 1f, 0f)

        val decision = matcher.processFrame(unknownQuery, timestampMs = 1000L)
        assertEquals(RecognitionStatus.UNKNOWN, decision.status)
        assertNull(decision.recognizedLandmarkId)
    }

    @Test
    fun testAmbiguousRejectionWhenMarginTooSmall() {
        val matcher = LandmarkMatcher(references, defaultThreshold = 0.50f, calibratedMargin = 0.15f)
        // Query equally close to A and B: [0.707, 0.707, 0, 0]
        val ambiguousQuery = floatArrayOf(0.7071f, 0.7071f, 0f, 0f)

        val decision = matcher.processFrame(ambiguousQuery, timestampMs = 1000L)
        assertEquals(RecognitionStatus.AMBIGUOUS, decision.status)
    }

    @Test
    fun testTemporalStabilityPromotionToIdentified() {
        val matcher = LandmarkMatcher(
            references = references,
            defaultThreshold = 0.70f,
            calibratedMargin = 0.08f,
            temporalWindowSize = 5,
            minQualifyingFrames = 3,
            minSpanDurationMs = 1000L
        )

        val queryA = floatArrayOf(0.99f, 0.10f, 0f, 0f).apply { normalize(this) }

        // Frame 1 at t = 0ms: Passes single-frame, but span is 0ms -> POSSIBLE
        val d1 = matcher.processFrame(queryA, timestampMs = 1000L)
        assertEquals(RecognitionStatus.POSSIBLE, d1.status)
        assertEquals("landmark-a", d1.recognizedLandmarkId)

        // Frame 2 at t = 500ms -> POSSIBLE (span = 500ms < 1000ms)
        val d2 = matcher.processFrame(queryA, timestampMs = 1500L)
        assertEquals(RecognitionStatus.POSSIBLE, d2.status)

        // Frame 3 at t = 1100ms -> IDENTIFIED (count >= 3, span = 1100ms >= 1000ms)
        val d3 = matcher.processFrame(queryA, timestampMs = 2100L)
        assertEquals(RecognitionStatus.IDENTIFIED, d3.status)
        assertEquals("landmark-a", d3.recognizedLandmarkId)
    }
}
