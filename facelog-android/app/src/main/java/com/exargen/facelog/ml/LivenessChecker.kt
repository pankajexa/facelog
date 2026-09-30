package com.exargen.facelog.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passive liveness detection using MiniFASNet.
 * Distinguishes real faces from printed photos, screen replay, and 3D masks.
 * Input: 80x80 face crop.
 * Output: liveness score (0.0 = spoof, 1.0 = real).
 *
 * This is optional — if the model file is missing, liveness is skipped.
 */
@Singleton
class LivenessChecker @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "LivenessChecker"
        private const val MODEL_PATH = "models/minifasnet_liveness.onnx"
        private const val INPUT_SIZE = 80
        private const val LIVENESS_THRESHOLD = 0.5f
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var initAttempted = false

    @Synchronized
    fun initialize() {
        if (session != null || initAttempted) return
        initAttempted = true

        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_PATH).readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(1)
            }
            session = ortEnv?.createSession(modelBytes, sessionOptions)
            Log.i(TAG, "Liveness model loaded successfully")
        } catch (e: Exception) {
            // Liveness model is optional - degrade gracefully
            Log.w(TAG, "Liveness model not available: ${e.message}")
            session = null
        }
    }

    fun checkLiveness(faceBitmap: Bitmap): LivenessResult {
        initialize()

        val sess = session
        if (sess == null) {
            // No model available - skip liveness check, assume real
            return LivenessResult(score = 1.0f, isReal = true, modelAvailable = false)
        }

        val env = ortEnv ?: return LivenessResult(score = 1.0f, isReal = true, modelAvailable = false)

        return try {
            val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            val floatBuffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
            val channelSize = INPUT_SIZE * INPUT_SIZE

            for (c in 0 until 3) {
                for (i in 0 until channelSize) {
                    val pixel = pixels[i]
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF).toFloat() / 255f
                        1 -> ((pixel shr 8) and 0xFF).toFloat() / 255f
                        2 -> (pixel and 0xFF).toFloat() / 255f
                        else -> 0f
                    }
                    floatBuffer.put(value)
                }
            }
            floatBuffer.rewind()

            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, inputShape)

            val inputName = sess.inputNames.first()
            val results = sess.run(mapOf(inputName to inputTensor))

            val outputTensor = results[0] as OnnxTensor
            val output = (outputTensor.value as Array<FloatArray>)[0]

            // Softmax to get probability
            val expReal = Math.exp(output[1].toDouble()).toFloat()
            val expFake = Math.exp(output[0].toDouble()).toFloat()
            val score = expReal / (expReal + expFake)

            inputTensor.close()
            results.close()

            LivenessResult(
                score = score,
                isReal = score >= LIVENESS_THRESHOLD,
                modelAvailable = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Liveness check failed: ${e.message}")
            LivenessResult(score = 1.0f, isReal = true, modelAvailable = false)
        }
    }

    fun close() {
        session?.close()
        session = null
        ortEnv?.close()
        ortEnv = null
    }
}

data class LivenessResult(
    val score: Float,
    val isReal: Boolean,
    val modelAvailable: Boolean = true,
)
