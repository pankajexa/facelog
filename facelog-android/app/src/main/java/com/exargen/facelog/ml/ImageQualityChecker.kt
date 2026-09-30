package com.exargen.facelog.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton

data class QualityResult(
    val sharpness: Float,
    val brightness: Float,
    val isAcceptable: Boolean,
    val feedback: String?,
    val score: Float,
)

@Singleton
class ImageQualityChecker @Inject constructor() {

    companion object {
        private const val SHARPNESS_THRESHOLD = 15f
        private const val MIN_BRIGHTNESS = 30f
        private const val MAX_BRIGHTNESS = 245f
    }

    /**
     * Assess the quality of a face crop from the given bitmap and bounding box.
     * Returns a QualityResult with sharpness, brightness, acceptability, and feedback.
     */
    fun assessFaceQuality(bitmap: Bitmap, faceBox: RectF): QualityResult {
        val crop = cropFaceRegion(bitmap, faceBox)

        val sharpness = computeSharpness(crop)
        val brightness = computeBrightness(crop)

        if (crop !== bitmap) {
            crop.recycle()
        }

        val sharpEnough = sharpness > SHARPNESS_THRESHOLD
        val brightEnough = brightness >= MIN_BRIGHTNESS
        val notTooBright = brightness <= MAX_BRIGHTNESS

        val feedback = when {
            !brightEnough -> "Need better lighting"
            !notTooBright -> "Too bright"
            !sharpEnough -> "Hold steady"
            else -> null
        }

        val isAcceptable = sharpEnough && brightEnough && notTooBright

        // Composite score: normalized sharpness (clamped to 0-1 range with 100 as max reference)
        val normalizedSharpness = (sharpness / 100f).coerceIn(0f, 1f)
        val score = normalizedSharpness

        return QualityResult(
            sharpness = sharpness,
            brightness = brightness,
            isAcceptable = isAcceptable,
            feedback = feedback,
            score = score,
        )
    }

    /**
     * Compute sharpness using Laplacian variance.
     * Higher variance = sharper image.
     */
    private fun computeSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to grayscale
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Apply 3x3 Laplacian kernel: [0,1,0],[1,-4,1],[0,1,0]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val laplacian = gray[idx - w] +       // top
                        gray[idx - 1] +                // left
                        -4f * gray[idx] +              // center
                        gray[idx + 1] +                // right
                        gray[idx + w]                  // bottom

                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count - mean * mean).toFloat()
        return variance.coerceAtLeast(0f)
    }

    /**
     * Compute mean brightness (grayscale) of the bitmap.
     */
    private fun computeBrightness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }

        return if (pixels.isNotEmpty()) sum.toFloat() / pixels.size else 0f
    }

    private fun cropFaceRegion(bitmap: Bitmap, faceBox: RectF): Bitmap {
        val left = faceBox.left.toInt().coerceAtLeast(0)
        val top = faceBox.top.toInt().coerceAtLeast(0)
        val right = faceBox.right.toInt().coerceAtMost(bitmap.width)
        val bottom = faceBox.bottom.toInt().coerceAtMost(bitmap.height)

        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return bitmap

        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }
}
