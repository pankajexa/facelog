package com.exargen.facelog.ml

import com.exargen.facelog.ui.screen.enrollment.FaceScanAngle
import javax.inject.Inject
import javax.inject.Singleton

enum class FaceGuideState {
    NO_FACE,
    FACE_TOO_FAR,
    FACE_TOO_CLOSE,
    FACE_OFF_CENTER,
    WRONG_ANGLE,
    LOW_QUALITY,
    ALIGNING,
    CAPTURED,
}

data class ValidationResult(
    val state: FaceGuideState,
    val message: String,
)

@Singleton
class FaceAngleValidator @Inject constructor() {

    companion object {
        // Face size as fraction of image width
        private const val MIN_FACE_SIZE = 0.22f
        private const val MAX_FACE_SIZE = 0.58f

        // Face center must be within this fraction of image center
        private const val CENTER_TOLERANCE = 0.15f

        // Yaw/pitch ranges per angle
        // yaw = horizontal ratio of nose offset from eye-midpoint
        // pitch = vertical ratio of nose below eye-midpoint relative to face height
        private val ANGLE_RANGES = mapOf(
            FaceScanAngle.CENTER to AngleRange(-0.12f..0.12f, 0.40f..0.65f),
            FaceScanAngle.LEFT to AngleRange(0.15f..0.60f, 0.30f..0.75f),
            FaceScanAngle.RIGHT to AngleRange(-0.60f..-0.15f, 0.30f..0.75f),
            FaceScanAngle.UP to AngleRange(-0.20f..0.20f, 0.20f..0.42f),
            FaceScanAngle.DOWN to AngleRange(-0.20f..0.20f, 0.62f..0.85f),
        )
    }

    private data class AngleRange(
        val yawRange: ClosedFloatingPointRange<Float>,
        val pitchRange: ClosedFloatingPointRange<Float>,
    )

    /**
     * Validate a detected face against the expected angle.
     * @param face the detected face with keypoints
     * @param imageWidth original image width in pixels
     * @param imageHeight original image height in pixels
     * @param targetAngle which angle we expect
     */
    fun validate(
        face: DetectedFace,
        imageWidth: Int,
        imageHeight: Int,
        targetAngle: FaceScanAngle,
    ): ValidationResult {
        val bbox = face.boundingBox

        // MediaPipe returns pixel coordinates, normalize to 0-1
        val faceWidthNorm = bbox.width() / imageWidth
        val faceHeightNorm = bbox.height() / imageHeight

        // Size check
        if (faceWidthNorm < MIN_FACE_SIZE) {
            return ValidationResult(FaceGuideState.FACE_TOO_FAR, "Move closer")
        }
        if (faceWidthNorm > MAX_FACE_SIZE) {
            return ValidationResult(FaceGuideState.FACE_TOO_CLOSE, "Move back")
        }

        // Position check — face center vs image center
        val faceCenterX = (bbox.left + bbox.right) / 2f / imageWidth
        val faceCenterY = (bbox.top + bbox.bottom) / 2f / imageHeight

        if (kotlin.math.abs(faceCenterX - 0.5f) > CENTER_TOLERANCE ||
            kotlin.math.abs(faceCenterY - 0.5f) > CENTER_TOLERANCE * 1.5f
        ) {
            return ValidationResult(FaceGuideState.FACE_OFF_CENTER, "Center your face")
        }

        // Angle check using keypoints
        // MediaPipe BlazeFace keypoints order: right eye, left eye, nose, mouth, right ear, left ear
        if (face.keypoints.size < 3) {
            // Not enough keypoints — accept as aligning
            return ValidationResult(FaceGuideState.ALIGNING, "Hold still...")
        }

        val rightEye = face.keypoints[0] // (x, y) in pixels
        val leftEye = face.keypoints[1]
        val nose = face.keypoints[2]

        val eyeMidX = (rightEye.first + leftEye.first) / 2f
        val eyeMidY = (rightEye.second + leftEye.second) / 2f

        val eyeDistance = kotlin.math.sqrt(
            (leftEye.first - rightEye.first).let { it * it } +
                    (leftEye.second - rightEye.second).let { it * it },
        )

        if (eyeDistance < 1f) {
            return ValidationResult(FaceGuideState.ALIGNING, "Hold still...")
        }

        // yaw: horizontal offset of nose from eye-midpoint, normalized by eye distance
        val yaw = (nose.first - eyeMidX) / eyeDistance
        // pitch: vertical offset of nose below eye-midpoint, normalized by face height
        val faceHeight = bbox.height()
        val pitch = if (faceHeight > 1f) (nose.second - eyeMidY) / faceHeight else 0.5f

        val ranges = ANGLE_RANGES[targetAngle] ?: return ValidationResult(
            FaceGuideState.ALIGNING,
            "Hold still...",
        )

        if (yaw !in ranges.yawRange || pitch !in ranges.pitchRange) {
            val instruction = when (targetAngle) {
                FaceScanAngle.CENTER -> "Look straight ahead"
                FaceScanAngle.LEFT -> "Turn head left"
                FaceScanAngle.RIGHT -> "Turn head right"
                FaceScanAngle.UP -> "Tilt head up"
                FaceScanAngle.DOWN -> "Tilt head down"
            }
            return ValidationResult(FaceGuideState.WRONG_ANGLE, instruction)
        }

        return ValidationResult(FaceGuideState.ALIGNING, "Hold still...")
    }
}
