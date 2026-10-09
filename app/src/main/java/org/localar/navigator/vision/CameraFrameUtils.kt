package org.localar.navigator.vision

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import java.nio.ByteBuffer

/**
 * Camera frame buffer extraction and format conversion utilities.
 * Implements Section 16.1 and 16.2 of spec.md.
 */
object CameraFrameUtils {

    /**
     * Converts an Android YUV_420_888 Image into an owned RGB Bitmap.
     * Handles row and pixel strides safely before the native frame is closed.
     */
    fun yuv420ToBitmap(image: Image): Bitmap {
        require(image.format == ImageFormat.YUV_420_888) {
            "Expected YUV_420_888 image format, was: ${image.format}"
        }

        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val argbArray = IntArray(width * height)

        for (y in 0 until height) {
            val yOffset = y * yRowStride
            val uvOffset = (y shr 1) * uvRowStride

            for (x in 0 until width) {
                val yIndex = yOffset + x * yPixelStride
                val uvIndex = uvOffset + (x shr 1) * uvPixelStride

                val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                // YUV to RGB color conversion formula
                var r = (yVal + 1.370705f * vVal).toInt()
                var g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt()
                var b = (yVal + 1.732446f * uVal).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                argbArray[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(argbArray, width, height, Bitmap.Config.ARGB_8888)
    }
}
