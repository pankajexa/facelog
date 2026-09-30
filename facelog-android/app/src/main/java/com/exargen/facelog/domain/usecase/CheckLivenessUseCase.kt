package com.exargen.facelog.domain.usecase

import android.graphics.Bitmap
import com.exargen.facelog.ml.LivenessChecker
import com.exargen.facelog.ml.LivenessResult
import javax.inject.Inject

/**
 * Standalone liveness check use case.
 */
class CheckLivenessUseCase @Inject constructor(
    private val livenessChecker: LivenessChecker,
) {
    operator fun invoke(faceBitmap: Bitmap): LivenessResult {
        return livenessChecker.checkLiveness(faceBitmap)
    }
}
