package com.exargen.facelog.ml

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class GalleryMatcher @Inject constructor() {

    companion object {
        private const val TAG = "GalleryMatcher"
        const val DEFAULT_THRESHOLD = 0.60f
        const val DISAMBIGUATION_MARGIN = 0.05f
    }

    data class PersonEmbeddings(
        val personId: String,
        val personName: String,
        val embeddings: List<FloatArray>,
    )

    data class MatchResult(
        val matched: Boolean,
        val personId: String?,
        val personName: String?,
        val similarity: Float,
    )

    fun findMatch(
        embedding: FloatArray,
        gallery: List<PersonEmbeddings>,
        threshold: Float = DEFAULT_THRESHOLD,
    ): MatchResult {
        if (gallery.isEmpty()) {
            return MatchResult(matched = false, personId = null, personName = null, similarity = 0f)
        }

        var bestPersonId: String? = null
        var bestPersonName: String? = null
        var bestSimilarity = -1f
        var secondBestSimilarity = -1f

        for (person in gallery) {
            // MAX similarity across all embeddings for this person
            var maxSim = -1f
            for (stored in person.embeddings) {
                val sim = cosineSimilarity(embedding, stored)
                if (sim > maxSim) maxSim = sim
            }

            Log.d(TAG, "  ${person.personName}: maxSim=${"%.4f".format(maxSim)} (${person.embeddings.size} embeddings)")

            if (maxSim > bestSimilarity) {
                secondBestSimilarity = bestSimilarity
                bestSimilarity = maxSim
                bestPersonId = person.personId
                bestPersonName = person.personName
            } else if (maxSim > secondBestSimilarity) {
                secondBestSimilarity = maxSim
            }
        }

        // Check threshold and disambiguation margin
        val aboveThreshold = bestSimilarity >= threshold
        val disambiguated = secondBestSimilarity < 0f ||
                (bestSimilarity - secondBestSimilarity) >= DISAMBIGUATION_MARGIN

        Log.d(TAG, "Best: $bestPersonName=${"%.4f".format(bestSimilarity)}, 2nd=${"%.4f".format(secondBestSimilarity)}, threshold=$threshold, above=$aboveThreshold, disambiguated=$disambiguated")

        return if (aboveThreshold && disambiguated) {
            MatchResult(
                matched = true,
                personId = bestPersonId,
                personName = bestPersonName,
                similarity = bestSimilarity,
            )
        } else {
            MatchResult(
                matched = false,
                personId = null,
                personName = null,
                similarity = bestSimilarity,
            )
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }
}
