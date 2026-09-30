package com.exargen.facelog.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aligns detected faces using affine transformation for ArcFace embedding extraction.
 * Uses 3-point alignment (eyes + nose) with the standard ArcFace 112x112 template.
 * Output: 112x112 RGB aligned face suitable for ArcFace models.
 */
@Singleton
class FaceAligner @Inject constructor() {

    companion object {
        const val OUTPUT_SIZE = 112

        // Standard ArcFace alignment template for 112x112 output.
        // These are the target positions where facial landmarks should land.
        // Person's right eye (left in image) → left position in output
        private const val DST_RIGHT_EYE_X = 38.2946f
        private const val DST_RIGHT_EYE_Y = 51.6963f
        // Person's left eye (right in image) → right position in output
        private const val DST_LEFT_EYE_X = 73.5318f
        private const val DST_LEFT_EYE_Y = 51.5014f
        // Nose tip → center-bottom in output
        private const val DST_NOSE_X = 56.0252f
        private const val DST_NOSE_Y = 71.7366f

        // Fallback 2-point positions (eyes only)
        private const val FALLBACK_LEFT_EYE_X = 0.35f
        private const val FALLBACK_LEFT_EYE_Y = 0.35f
        private const val FALLBACK_RIGHT_EYE_X = 0.65f
        private const val FALLBACK_RIGHT_EYE_Y = 0.35f
    }

    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Align face using keypoints.
     * Prefers 3-point alignment (eyes + nose) for best accuracy.
     * Falls back to 2-point (eyes only) or simple crop if keypoints unavailable.
     *
     * MediaPipe BlazeFace keypoint order:
     *   0 = right eye (person's anatomical right)
     *   1 = left eye (person's anatomical left)
     *   2 = nose tip
     *   3 = mouth center
     *   4 = right ear tragion
     *   5 = left ear tragion
     */
    fun align(
        bitmap: Bitmap,
        face: DetectedFace,
    ): Bitmap {
        return when {
            face.keypoints.size >= 3 -> alignWith3Points(bitmap, face.keypoints)
            face.keypoints.size >= 2 -> alignWith2Points(bitmap, face.keypoints)
            else -> cropAndResize(bitmap, face.boundingBox)
        }
    }

    /**
     * 3-point affine alignment using eyes + nose.
     * Computes the affine matrix that maps source keypoints to the standard ArcFace template.
     * Uses Canvas to render the transformed bitmap directly onto a 112x112 output.
     */
    private fun alignWith3Points(
        bitmap: Bitmap,
        keypoints: List<Pair<Float, Float>>,
    ): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Source points in pixel coordinates (keypoints are normalized 0..1)
        val srcRightEye = keypoints[0]  // person's right eye
        val srcLeftEye = keypoints[1]   // person's left eye
        val srcNose = keypoints[2]      // nose tip

        val src = floatArrayOf(
            srcRightEye.first * w, srcRightEye.second * h,
            srcLeftEye.first * w, srcLeftEye.second * h,
            srcNose.first * w, srcNose.second * h,
        )

        val dst = floatArrayOf(
            DST_RIGHT_EYE_X, DST_RIGHT_EYE_Y,
            DST_LEFT_EYE_X, DST_LEFT_EYE_Y,
            DST_NOSE_X, DST_NOSE_Y,
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 3)

        if (!success) {
            // Fall back to 2-point if poly mapping fails
            return alignWith2Points(bitmap, keypoints)
        }

        return renderAligned(bitmap, matrix)
    }

    /**
     * 2-point alignment using eyes only (fallback).
     * Handles rotation and scale but not the full affine warp.
     */
    private fun alignWith2Points(
        bitmap: Bitmap,
        keypoints: List<Pair<Float, Float>>,
    ): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Source: person's right eye (index 0) and left eye (index 1)
        val srcRightEyeX = keypoints[0].first * w
        val srcRightEyeY = keypoints[0].second * h
        val srcLeftEyeX = keypoints[1].first * w
        val srcLeftEyeY = keypoints[1].second * h

        val src = floatArrayOf(
            srcRightEyeX, srcRightEyeY,
            srcLeftEyeX, srcLeftEyeY,
        )

        val dst = floatArrayOf(
            DST_RIGHT_EYE_X, DST_RIGHT_EYE_Y,
            DST_LEFT_EYE_X, DST_LEFT_EYE_Y,
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 2)

        return renderAligned(bitmap, matrix)
    }

    /**
     * Renders the source bitmap through the affine matrix onto a fixed 112x112 output.
     * This is the CRITICAL fix: Canvas.drawBitmap renders exactly onto the target size,
     * unlike Bitmap.createBitmap which produces a variably-sized output.
     */
    private fun renderAligned(bitmap: Bitmap, matrix: Matrix): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, matrix, renderPaint)
        return output
    }

    /**
     * Simple crop + resize fallback when no keypoints are available.
     */
    private fun cropAndResize(bitmap: Bitmap, bbox: RectF): Bitmap {
        val padding = 0.2f
        val w = bbox.width()
        val h = bbox.height()

        val left = (bbox.left - w * padding).toInt().coerceAtLeast(0)
        val top = (bbox.top - h * padding).toInt().coerceAtLeast(0)
        val right = (bbox.right + w * padding).toInt().coerceAtMost(bitmap.width)
        val bottom = (bbox.bottom + h * padding).toInt().coerceAtMost(bitmap.height)

        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        return Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE, OUTPUT_SIZE, true)
    }
}
