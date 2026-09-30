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

class RecognizeMultipleFacesUseCase @Inject constructor(
    private val faceDetector: FaceDetector,
    private val faceAligner: FaceAligner,
    private val faceEmbedder: FaceEmbedder,
    private val livenessChecker: LivenessChecker,
    private val galleryMatcher: GalleryMatcher,
    private val personRepository: PersonRepository,
) {
    companion object {
        private const val TAG = "RecognizeMultiFace"
        private const val MAX_FACES = 5
    }

    data class MultiFaceResult(
        val results: List<RecognitionResult>,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    suspend operator fun invoke(
        bitmap: Bitmap,
        recognitionThreshold: Float = GalleryMatcher.DEFAULT_THRESHOLD,
        livenessThreshold: Float = 0.5f,
    ): MultiFaceResult {
        val results = mutableListOf<RecognitionResult>()

        try {
            // 1. Detect all faces
            val allFaces = faceDetector.detect(bitmap)
            if (allFaces.isEmpty()) {
                return MultiFaceResult(emptyList(), bitmap.width, bitmap.height)
            }

            // Take top MAX_FACES by confidence
            val faces = allFaces.sortedByDescending { it.confidence }.take(MAX_FACES)
            Log.d(TAG, "Detected ${allFaces.size} faces, processing ${faces.size}")

            // 2. Load gallery ONCE
            val personsWithEmbeddings = personRepository.getAllPersonsWithEmbeddings()
            val gallery = personsWithEmbeddings.map { pwe ->
                GalleryMatcher.PersonEmbeddings(
                    personId = pwe.person.id,
                    personName = pwe.person.name,
                    embeddings = pwe.embeddings.map { it.embedding },
                )
            }
            Log.d(TAG, "Gallery: ${gallery.size} persons, ${gallery.sumOf { it.embeddings.size }} total embeddings")

            // 3. Process each face
            for (face in faces) {
                try {
                    Log.d(TAG, "Processing face: bbox=${face.boundingBox}, confidence=${face.confidence}")

                    // Align
                    val aligned = faceAligner.align(bitmap, face)

                    // Liveness
                    val liveness = livenessChecker.checkLiveness(aligned)
                    if (liveness.modelAvailable && !liveness.isReal) {
                        Log.d(TAG, "Spoof detected: score=${liveness.score}")
                        results.add(
                            RecognitionResult.SpoofDetected(
                                livenessScore = liveness.score,
                                boundingBox = face.boundingBox,
                            )
                        )
                        continue
                    }

                    // Embed
                    val embedding = faceEmbedder.extractEmbedding(aligned)
                    val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
                    Log.d(TAG, "Embedding extracted: norm=$norm, dim=${embedding.size}")

                    // Match
                    val match = galleryMatcher.findMatch(
                        embedding = embedding,
                        gallery = gallery,
                        threshold = recognitionThreshold,
                    )

                    Log.d(TAG, "Match result: matched=${match.matched}, person=${match.personName}, similarity=${match.similarity}")

                    if (match.matched) {
                        results.add(
                            RecognitionResult.Recognized(
                                personId = match.personId!!,
                                personName = match.personName!!,
                                similarity = match.similarity,
                                livenessScore = liveness.score,
                                boundingBox = face.boundingBox,
                            )
                        )
                    } else {
                        results.add(
                            RecognitionResult.NotRecognized(
                                bestSimilarity = match.similarity,
                                boundingBox = face.boundingBox,
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process face: ${e.message}", e)
                    results.add(RecognitionResult.Error(e.message ?: "Face processing failed"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multi-face recognition failed: ${e.message}", e)
            return MultiFaceResult(
                listOf(RecognitionResult.Error(e.message ?: "Recognition failed")),
                bitmap.width,
                bitmap.height,
            )
        }

        return MultiFaceResult(results, bitmap.width, bitmap.height)
    }
}
