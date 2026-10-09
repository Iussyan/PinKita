package org.localar.navigator.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlin.math.min

/**
 * On-device neural network image embedder using MediaPipe Tasks Vision (MobileNetV3).
 * Implements Section 4 (Vision), Section 15 (Embedding inference), and Section 16.3 of spec.md.
 */
class MediaPipeEmbedder(
    context: Context,
    modelAssetPath: String = "models/mobilenet_v3_small.tflite"
) : AutoCloseable {

    private val embedder: ImageEmbedder

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()

        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build()

        embedder = ImageEmbedder.createFromOptions(context, options)
    }

    /**
     * Extracts an L2-normalized float32 embedding vector from an upright camera bitmap.
     * Applies square center crop before inference (Section 15.3).
     */
    fun embed(bitmap: Bitmap): FloatArray {
        val squareBitmap = centerCropSquare(bitmap)
        val mpImage = BitmapImageBuilder(squareBitmap).build()

        val result = embedder.embed(mpImage)
        val embeddings = result.embeddingResult().embeddings()
        if (embeddings.isEmpty()) {
            throw IllegalStateException("MediaPipe ImageEmbedder returned no embeddings")
        }

        val firstEmbedding = embeddings[0]
        val floatValues = firstEmbedding.floatEmbedding()
            ?: throw IllegalStateException("Model did not return float embeddings")

        return floatValues
    }

    /**
     * Preprocessing center-crop per Section 15.3 of spec.md.
     */
    private fun centerCropSquare(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val cropSize = min(width, height)

        val cropX = (width - cropSize) / 2
        val cropY = (height - cropSize) / 2

        val cropped = Bitmap.createBitmap(src, cropX, cropY, cropSize, cropSize)
        // If cropSize != 224, scale to 224x224 standard model input
        return if (cropSize != 224) {
            Bitmap.createScaledBitmap(cropped, 224, 224, true)
        } else {
            cropped
        }
    }

    override fun close() {
        embedder.close()
    }
}
