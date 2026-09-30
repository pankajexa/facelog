package com.exargen.facelog.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector as MpFaceDetector
import javax.inject.Inject
import javax.inject.Singleton

data class DetectedFace(
    val boundingBox: RectF,
    val confidence: Float,
    val keypoints: List<Pair<Float, Float>>,
)

@Singleton
class FaceDetector @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "FaceDetector"
    }

    private var detector: MpFaceDetector? = null
    private var initFailed = false

    @Synchronized
    fun initialize() {
        if (detector != null || initFailed) return

        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("models/blaze_face_short_range.tflite")
                .build()

            val options = MpFaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.3f)
                .build()

            detector = MpFaceDetector.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe face detector initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face detector: ${e.message}", e)
            initFailed = true
            detector = null
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedFace> {
        initialize()

        val det = detector ?: return emptyList()

        return try {
            // Ensure bitmap is in software rendering mode (not hardware)
            val safeBitmap = if (bitmap.config == null || bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            val mpImage = BitmapImageBuilder(safeBitmap).build()
            val result = det.detect(mpImage)

            result.detections().map { detection ->
                val bbox = detection.boundingBox()
                val keypoints = detection.keypoints().orElse(emptyList()).map { kp ->
                    kp.x() to kp.y()
                }
                DetectedFace(
                    boundingBox = RectF(
                        bbox.left.toFloat(),
                        bbox.top.toFloat(),
                        bbox.right.toFloat(),
                        bbox.bottom.toFloat(),
                    ),
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f,
                    keypoints = keypoints,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}")
            emptyList()
        }
    }

    fun close() {
        detector?.close()
        detector = null
    }
}
