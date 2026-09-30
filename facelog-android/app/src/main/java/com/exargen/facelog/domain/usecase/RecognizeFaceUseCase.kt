package com.exargen.facelog.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.exargen.facelog.data.repository.PersonRepository
import com.exargen.facelog.domain.model.RecognitionResult
import com.exargen.facelog.ml.FaceAligner
import com.exargen.facelog.ml.FaceDetector
import com.exargen.facelog.ml.FaceEmbedder
import com.exargen.facelog.ml.GalleryMatcher
import com.exargen.facelog.ml.LivenessChecker
import javax.inject.Inject
import kotlin.math.sqrt

class RecognizeFaceUseCase @Inject constructor(
    private val faceDetector: FaceDetector,
    private val faceAligner: FaceAligner,
    private val faceEmbedder: FaceEmbedder,
    private val livenessChecker: LivenessChecker,
    private val galleryMatcher: GalleryMatcher,
    private val personRepository: PersonRepository,
) {
    companion object {
        private const val TAG = "RecognizeFace"
    }

    suspend operator fun invoke(
        bitmap: Bitmap,
        recognitionThreshold: Float = GalleryMatcher.DEFAULT_THRESHOLD,
        livenessThreshold: Float = 0.5f,
    ): RecognitionResult {
        return try {
            // 1. Detect face
            val faces = faceDetector.detect(bitmap)
            if (faces.isEmpty()) {
                return RecognitionResult.NoFaceDetected
            }

            val face = faces.first()
            Log.d(TAG, "Face detected: bbox=${face.boundingBox}, confidence=${face.confidence}, keypoints=${face.keypoints.size}")

            // 2. Align face
            val aligned = faceAligner.align(bitmap, face)

            // 3. Check liveness
            val liveness = livenessChecker.checkLiveness(aligned)
            if (liveness.modelAvailable && !liveness.isReal) {
                Log.d(TAG, "Spoof detected: score=${liveness.score}")
                return RecognitionResult.SpoofDetected(
                    livenessScore = liveness.score,
                    boundingBox = face.boundingBox,
                )
            }

            // 4. Extract embedding
            val embedding = faceEmbedder.extractEmbedding(aligned)
            val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
            Log.d(TAG, "Embedding extracted: norm=$norm, dim=${embedding.size}")

            // 5. Build gallery from PersonWithEmbeddings
            val personsWithEmbeddings = personRepository.getAllPersonsWithEmbeddings()
            val gallery = personsWithEmbeddings.map { pwe ->
                GalleryMatcher.PersonEmbeddings(
                    personId = pwe.person.id,
                    personName = pwe.person.name,
                    embeddings = pwe.embeddings.map { it.embedding },
                )
            }
            Log.d(TAG, "Gallery: ${gallery.size} persons, ${gallery.sumOf { it.embeddings.size }} total embeddings")

            val match = galleryMatcher.findMatch(
                embedding = embedding,
                gallery = gallery,
                threshold = recognitionThreshold,
            )

            Log.d(TAG, "Match result: matched=${match.matched}, person=${match.personName}, similarity=${match.similarity}")

            if (match.matched) {
                RecognitionResult.Recognized(
                    personId = match.personId!!,
                    personName = match.personName!!,
                    similarity = match.similarity,
                    livenessScore = liveness.score,
                    boundingBox = face.boundingBox,
                )
            } else {
                RecognitionResult.NotRecognized(
                    bestSimilarity = match.similarity,
                    boundingBox = face.boundingBox,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed: ${e.message}", e)
            RecognitionResult.Error(e.message ?: "Recognition failed")
        }
    }
}
