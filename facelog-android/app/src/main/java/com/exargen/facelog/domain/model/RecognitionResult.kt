package com.exargen.facelog.domain.model

import android.graphics.RectF

sealed class RecognitionResult {
    data class Recognized(
        val personId: String,
        val personName: String,
        val similarity: Float,
        val livenessScore: Float,
        val boundingBox: RectF,
    ) : RecognitionResult()

    data class NotRecognized(
        val bestSimilarity: Float,
        val boundingBox: RectF,
    ) : RecognitionResult()

    data class SpoofDetected(
        val livenessScore: Float,
        val boundingBox: RectF,
    ) : RecognitionResult()

    data object NoFaceDetected : RecognitionResult()

    data class Error(val message: String) : RecognitionResult()
}
