package com.exargen.facelog.util

object Constants {
    // Enrollment limits
    const val MAX_PERSONS = 10
    const val EMBEDDINGS_PER_PERSON = 5

    // Recognition settings
    const val DEFAULT_RECOGNITION_THRESHOLD = 0.60f
    const val DEFAULT_LIVENESS_THRESHOLD = 0.5f
    const val RECOGNITION_COOLDOWN_MS = 3000L

    // ML model paths (in assets)
    const val MODEL_FACE_RECOGNITION = "models/w600k_mbf.onnx"
    const val MODEL_LIVENESS = "models/minifasnet_liveness.onnx"
    const val MODEL_FACE_DETECTOR = "models/blaze_face_short_range.tflite"

    // Embedding dimensions
    const val EMBEDDING_DIM = 512
}
