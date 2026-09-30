package com.exargen.facelog.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Extracts 512-d face embeddings using ArcFace (ResNet100) via ONNX Runtime.
 * Input: 112x112 aligned RGB face image.
 * Output: L2-normalized 512-d float array.
 *
 * The model is copied from assets to internal storage on first use
 * to enable memory-efficient file-based loading instead of byte array.
 */
@Singleton
class FaceEmbedder @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "FaceEmbedder"
        private const val MODEL_ASSET_PATH = "models/w600k_mbf.onnx"
        private const val MODEL_FILE_NAME = "w600k_mbf.onnx"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_DIM = 512
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    @Synchronized
    fun initialize() {
        if (session != null) return

        try {
            ortEnv = OrtEnvironment.getEnvironment()

            // Copy model from assets to files dir for memory-efficient loading
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.i(TAG, "Copying ONNX model from assets to ${modelFile.absolutePath}")
                context.assets.open(MODEL_ASSET_PATH).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                Log.i(TAG, "Model copied: ${modelFile.length()} bytes")
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
            }

            // Load from file path — much more memory-efficient than readBytes()
            session = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            Log.i(TAG, "ONNX session created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceEmbedder: ${e.message}", e)
            session = null
        }
    }

    fun extractEmbedding(alignedFace: Bitmap): FloatArray {
        initialize()

        val env = ortEnv ?: throw IllegalStateException("ORT environment not initialized")
        val sess = session ?: throw IllegalStateException("ORT session not initialized — model may have failed to load")

        // Preprocess: resize to 112x112, convert to CHW float tensor, normalize
        val resized = if (alignedFace.width != INPUT_SIZE || alignedFace.height != INPUT_SIZE) {
            Bitmap.createScaledBitmap(alignedFace, INPUT_SIZE, INPUT_SIZE, true)
        } else {
            alignedFace
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Convert to CHW format with normalization: (pixel - 127.5) / 128.0
        val floatBuffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE

        for (c in 0 until 3) {
            for (i in 0 until channelSize) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> ((pixel shr 16) and 0xFF).toFloat()  // R
                    1 -> ((pixel shr 8) and 0xFF).toFloat()   // G
                    2 -> (pixel and 0xFF).toFloat()            // B
                    else -> 0f
                }
                floatBuffer.put((value - 127.5f) / 128.0f)
            }
        }
        floatBuffer.rewind()

        val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, inputShape)

        val inputName = sess.inputNames.first()
        val results = sess.run(mapOf(inputName to inputTensor))

        val outputTensor = results[0] as OnnxTensor
        val embedding = (outputTensor.value as Array<FloatArray>)[0]

        // L2 normalize
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
        if (norm > 0f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        inputTensor.close()
        results.close()

        return embedding
    }

    fun close() {
        session?.close()
        session = null
        ortEnv?.close()
        ortEnv = null
    }
}
