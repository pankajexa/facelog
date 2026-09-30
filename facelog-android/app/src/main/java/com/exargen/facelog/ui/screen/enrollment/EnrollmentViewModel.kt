package com.exargen.facelog.ui.screen.enrollment

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.local.entity.FaceEmbeddingEntity
import com.exargen.facelog.data.local.entity.PersonEntity
import com.exargen.facelog.data.repository.PersonRepository
import com.exargen.facelog.ml.DetectedFace
import com.exargen.facelog.ml.FaceAligner
import com.exargen.facelog.ml.FaceAngleValidator
import com.exargen.facelog.ml.FaceDetector
import com.exargen.facelog.ml.FaceEmbedder
import com.exargen.facelog.ml.FaceGuideState
import com.exargen.facelog.ml.ImageQualityChecker
import com.exargen.facelog.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.sqrt

enum class EnrollmentPhase {
    NAME_ENTRY,
    FACE_SCAN,
    PROCESSING,
    SUCCESS,
}

enum class FaceScanAngle(val label: String, val instruction: String) {
    CENTER("center", "Look straight at the camera"),
    LEFT("left", "Turn your head slightly left"),
    RIGHT("right", "Turn your head slightly right"),
    UP("up", "Tilt your head slightly up"),
    DOWN("down", "Tilt your head slightly down"),
}

data class EnrollmentUiState(
    val name: String = "",
    val phase: EnrollmentPhase = EnrollmentPhase.NAME_ENTRY,
    val currentAngleIndex: Int = 0,
    val faceDetected: Boolean = false,
    val consecutiveDetections: Int = 0,
    val capturedEmbeddings: List<FloatArray> = emptyList(),
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val enrolledPersonNames: List<String> = emptyList(),
    val enrolledCount: Int = 0,
    val savedPhotoPath: String? = null,
    val guideState: FaceGuideState = FaceGuideState.NO_FACE,
    val alignProgress: Float = 0f,
    val showCaptureFlash: Boolean = false,
    val feedbackMessage: String = "Position your face in the oval",
    val qualityScore: Float = 0f,
) {
    val currentAngle: FaceScanAngle
        get() = FaceScanAngle.entries[currentAngleIndex.coerceIn(0, FaceScanAngle.entries.size - 1)]

    val totalAngles: Int get() = FaceScanAngle.entries.size
    val capturedCount: Int get() = capturedEmbeddings.size
    val canEnroll: Boolean get() = enrolledCount < Constants.MAX_PERSONS
}

private data class CandidateFrame(
    val bitmap: Bitmap,
    val face: DetectedFace,
    val qualityScore: Float,
)

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val faceDetector: FaceDetector,
    private val faceAligner: FaceAligner,
    private val faceEmbedder: FaceEmbedder,
    private val personRepository: PersonRepository,
    private val faceAngleValidator: FaceAngleValidator,
    private val imageQualityChecker: ImageQualityChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    private val _hapticEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hapticEvent: SharedFlow<Unit> = _hapticEvent.asSharedFlow()

    private var isProcessingFrame = false
    private var centerBitmap: Bitmap? = null
    private var noFaceFrameCount = 0

    private val candidateFrames = mutableListOf<CandidateFrame>()
    private val MAX_CANDIDATES = 10
    private val TOP_CANDIDATES_FOR_EMBEDDING = 3

    companion object {
        private const val REQUIRED_CONSECUTIVE = 5
        private const val NO_FACE_TOLERANCE = 3
        private const val MIN_FACE_CONFIDENCE = 0.55f
        private const val SELF_VERIFY_THRESHOLD = 0.25f
    }

    init {
        observePersons()
    }

    private fun observePersons() {
        viewModelScope.launch {
            personRepository.getAllPersons().collect { persons ->
                _uiState.update {
                    it.copy(
                        enrolledPersonNames = persons.map { p -> p.name },
                        enrolledCount = persons.size,
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, statusMessage = null) }
    }

    fun proceedToFaceScan() {
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter a name", isError = true) }
            return
        }
        if (!_uiState.value.canEnroll) {
            _uiState.update { it.copy(statusMessage = "Maximum ${Constants.MAX_PERSONS} persons reached", isError = true) }
            return
        }

        centerBitmap = null
        noFaceFrameCount = 0
        clearCandidateFrames()
        _uiState.update {
            it.copy(
                phase = EnrollmentPhase.FACE_SCAN,
                currentAngleIndex = 0,
                faceDetected = false,
                consecutiveDetections = 0,
                capturedEmbeddings = emptyList(),
                statusMessage = null,
                guideState = FaceGuideState.NO_FACE,
                alignProgress = 0f,
                showCaptureFlash = false,
                feedbackMessage = "Position your face in the oval",
                qualityScore = 0f,
            )
        }
    }

    fun onFrameAvailable(bitmap: Bitmap) {
        val state = _uiState.value
        if (state.phase != EnrollmentPhase.FACE_SCAN || isProcessingFrame) return
        if (state.showCaptureFlash) return

        isProcessingFrame = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val faces = faceDetector.detect(bitmap)

                if (faces.isEmpty()) {
                    noFaceFrameCount++
                    if (noFaceFrameCount > NO_FACE_TOLERANCE) {
                        _uiState.update {
                            it.copy(
                                faceDetected = false,
                                consecutiveDetections = 0,
                                guideState = FaceGuideState.NO_FACE,
                                alignProgress = 0f,
                                feedbackMessage = "Position your face in the oval",
                                qualityScore = 0f,
                            )
                        }
                    }
                    return@launch
                }

                noFaceFrameCount = 0

                val face = faces.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                } ?: faces.first()

                // Confidence gate
                if (face.confidence < MIN_FACE_CONFIDENCE) {
                    val reduced = (_uiState.value.consecutiveDetections - 1).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            faceDetected = true,
                            consecutiveDetections = reduced,
                            guideState = FaceGuideState.LOW_QUALITY,
                            alignProgress = reduced.toFloat() / REQUIRED_CONSECUTIVE,
                            feedbackMessage = "Hold steady",
                            qualityScore = 0f,
                        )
                    }
                    return@launch
                }

                val targetAngle = _uiState.value.currentAngle
                val validation = faceAngleValidator.validate(
                    face = face,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    targetAngle = targetAngle,
                )

                when (validation.state) {
                    FaceGuideState.ALIGNING -> {
                        // Quality check
                        val quality = imageQualityChecker.assessFaceQuality(bitmap, face.boundingBox)

                        if (!quality.isAcceptable) {
                            val reduced = (_uiState.value.consecutiveDetections - 1).coerceAtLeast(0)
                            _uiState.update {
                                it.copy(
                                    faceDetected = true,
                                    consecutiveDetections = reduced,
                                    guideState = FaceGuideState.LOW_QUALITY,
                                    alignProgress = reduced.toFloat() / REQUIRED_CONSECUTIVE,
                                    feedbackMessage = quality.feedback ?: "Hold steady",
                                    qualityScore = quality.score,
                                )
                            }
                            return@launch
                        }

                        // Quality is acceptable — add to candidates
                        addCandidateFrame(bitmap, face, quality.score)

                        val newCount = _uiState.value.consecutiveDetections + 1
                        val progress = newCount.toFloat() / REQUIRED_CONSECUTIVE
                        _uiState.update {
                            it.copy(
                                faceDetected = true,
                                consecutiveDetections = newCount,
                                guideState = FaceGuideState.ALIGNING,
                                alignProgress = progress.coerceIn(0f, 1f),
                                feedbackMessage = "Hold still...",
                                qualityScore = quality.score,
                            )
                        }

                        if (newCount >= REQUIRED_CONSECUTIVE) {
                            captureCurrentAngle(bitmap)
                        }
                    }

                    FaceGuideState.FACE_TOO_FAR,
                    FaceGuideState.FACE_TOO_CLOSE,
                    FaceGuideState.FACE_OFF_CENTER,
                    FaceGuideState.WRONG_ANGLE,
                    -> {
                        _uiState.update {
                            it.copy(
                                faceDetected = true,
                                consecutiveDetections = 0,
                                guideState = validation.state,
                                alignProgress = 0f,
                                feedbackMessage = validation.message,
                                qualityScore = 0f,
                            )
                        }
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                faceDetected = false,
                                consecutiveDetections = 0,
                                guideState = validation.state,
                                alignProgress = 0f,
                                feedbackMessage = validation.message,
                                qualityScore = 0f,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        faceDetected = false,
                        consecutiveDetections = 0,
                        guideState = FaceGuideState.NO_FACE,
                        alignProgress = 0f,
                        qualityScore = 0f,
                    )
                }
            } finally {
                isProcessingFrame = false
            }
        }
    }

    private fun addCandidateFrame(bitmap: Bitmap, face: DetectedFace, qualityScore: Float) {
        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        candidateFrames.add(CandidateFrame(bitmapCopy, face, qualityScore))

        // Evict lowest quality if over max
        if (candidateFrames.size > MAX_CANDIDATES) {
            val worst = candidateFrames.minByOrNull { it.qualityScore }
            if (worst != null) {
                candidateFrames.remove(worst)
                worst.bitmap.recycle()
            }
        }
    }

    private fun clearCandidateFrames() {
        candidateFrames.forEach { it.bitmap.recycle() }
        candidateFrames.clear()
    }

    private suspend fun captureCurrentAngle(bitmap: Bitmap) {
        try {
            // Use multi-frame averaging from candidate frames
            val embedding = computeAveragedEmbedding()

            if (embedding == null) {
                // Fallback: use current frame directly
                val faces = faceDetector.detect(bitmap)
                if (faces.isEmpty()) {
                    _uiState.update { it.copy(consecutiveDetections = 0, alignProgress = 0f) }
                    return
                }
                val face = faces.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                } ?: faces.first()
                val aligned = faceAligner.align(bitmap, face)
                val fallbackEmbedding = faceEmbedder.extractEmbedding(aligned)
                proceedWithEmbedding(fallbackEmbedding, bitmap)
                return
            }

            proceedWithEmbedding(embedding, bitmap)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Capture failed: ${e.message}",
                    isError = true,
                    consecutiveDetections = 0,
                    showCaptureFlash = false,
                    alignProgress = 0f,
                )
            }
        }
    }

    private fun computeAveragedEmbedding(): FloatArray? {
        if (candidateFrames.isEmpty()) return null

        // Sort by quality descending, take top N
        val topCandidates = candidateFrames
            .sortedByDescending { it.qualityScore }
            .take(TOP_CANDIDATES_FOR_EMBEDDING)

        val embeddings = mutableListOf<FloatArray>()
        for (candidate in topCandidates) {
            try {
                val aligned = faceAligner.align(candidate.bitmap, candidate.face)
                val emb = faceEmbedder.extractEmbedding(aligned)
                embeddings.add(emb)
            } catch (_: Exception) {
                // Skip failed frames
            }
        }

        if (embeddings.isEmpty()) return null

        // Average element-wise
        val dim = embeddings[0].size
        val averaged = FloatArray(dim)
        for (emb in embeddings) {
            for (i in 0 until dim) {
                averaged[i] += emb[i]
            }
        }
        val count = embeddings.size.toFloat()
        for (i in 0 until dim) {
            averaged[i] /= count
        }

        // L2 normalize
        val norm = sqrt(averaged.sumOf { (it * it).toDouble() }.toFloat())
        if (norm > 0f) {
            for (i in averaged.indices) {
                averaged[i] /= norm
            }
        }

        return averaged
    }

    private suspend fun proceedWithEmbedding(embedding: FloatArray, bitmap: Bitmap) {
        val currentIndex = _uiState.value.currentAngleIndex
        val newEmbeddings = _uiState.value.capturedEmbeddings + embedding

        // Save center bitmap for thumbnail
        if (currentIndex == 0) {
            centerBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        // Show capture flash and haptic
        _uiState.update {
            it.copy(
                showCaptureFlash = true,
                guideState = FaceGuideState.CAPTURED,
                feedbackMessage = "Captured!",
            )
        }
        _hapticEvent.tryEmit(Unit)

        // Clean up candidates for this angle
        clearCandidateFrames()

        // Hold captured state for 600ms
        kotlinx.coroutines.delay(600)

        if (currentIndex < FaceScanAngle.entries.size - 1) {
            val nextAngle = FaceScanAngle.entries[currentIndex + 1]
            _uiState.update {
                it.copy(
                    capturedEmbeddings = newEmbeddings,
                    currentAngleIndex = currentIndex + 1,
                    consecutiveDetections = 0,
                    faceDetected = false,
                    showCaptureFlash = false,
                    guideState = FaceGuideState.NO_FACE,
                    alignProgress = 0f,
                    feedbackMessage = nextAngle.instruction,
                    qualityScore = 0f,
                )
            }
        } else {
            // All angles captured — self-verify before saving
            _uiState.update {
                it.copy(
                    capturedEmbeddings = newEmbeddings,
                    phase = EnrollmentPhase.PROCESSING,
                    showCaptureFlash = false,
                )
            }

            if (selfVerifyEmbeddings(newEmbeddings)) {
                saveEnrollment(newEmbeddings)
            } else {
                // Verification failed — reset
                _uiState.update {
                    it.copy(
                        phase = EnrollmentPhase.FACE_SCAN,
                        currentAngleIndex = 0,
                        capturedEmbeddings = emptyList(),
                        consecutiveDetections = 0,
                        faceDetected = false,
                        guideState = FaceGuideState.NO_FACE,
                        alignProgress = 0f,
                        statusMessage = "Poor capture quality. Please retry in better lighting with a steady hand.",
                        isError = true,
                        feedbackMessage = "Position your face in the oval",
                        qualityScore = 0f,
                    )
                }
            }
        }
    }

    private fun selfVerifyEmbeddings(embeddings: List<FloatArray>): Boolean {
        if (embeddings.size < 2) return true

        val centerEmbedding = embeddings[0]
        for (i in 1 until embeddings.size) {
            val sim = cosineSimilarity(centerEmbedding, embeddings[i])
            if (sim < SELF_VERIFY_THRESHOLD) {
                return false
            }
        }
        return true
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

    private suspend fun saveEnrollment(embeddings: List<FloatArray>) {
        try {
            val name = _uiState.value.name.trim()
            val personId = UUID.randomUUID().toString()

            val photoPath = saveThumbnail(personId)

            val person = PersonEntity(
                id = personId,
                name = name,
                photoPath = photoPath,
            )

            val embeddingEntities = embeddings.mapIndexed { index, emb ->
                FaceEmbeddingEntity(
                    personId = personId,
                    embedding = emb,
                    angleLabel = FaceScanAngle.entries[index].label,
                )
            }

            personRepository.insertPerson(person)
            personRepository.insertEmbeddings(embeddingEntities)

            _uiState.update {
                it.copy(
                    phase = EnrollmentPhase.SUCCESS,
                    savedPhotoPath = photoPath,
                    statusMessage = "Successfully enrolled $name!",
                    isError = false,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    phase = EnrollmentPhase.FACE_SCAN,
                    currentAngleIndex = 0,
                    capturedEmbeddings = emptyList(),
                    consecutiveDetections = 0,
                    statusMessage = "Save failed: ${e.message}",
                    isError = true,
                )
            }
        }
    }

    private fun saveThumbnail(personId: String): String? {
        val bitmap = centerBitmap ?: return null
        return try {
            val dir = File(context.filesDir, "faces")
            dir.mkdirs()
            val file = File(dir, "$personId.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun enrollAnother() {
        centerBitmap = null
        noFaceFrameCount = 0
        clearCandidateFrames()
        _uiState.update {
            it.copy(
                name = "",
                phase = EnrollmentPhase.NAME_ENTRY,
                currentAngleIndex = 0,
                capturedEmbeddings = emptyList(),
                consecutiveDetections = 0,
                faceDetected = false,
                statusMessage = null,
                savedPhotoPath = null,
                guideState = FaceGuideState.NO_FACE,
                alignProgress = 0f,
                showCaptureFlash = false,
                feedbackMessage = "Position your face in the oval",
                qualityScore = 0f,
            )
        }
    }

    fun backToNameEntry() {
        centerBitmap = null
        noFaceFrameCount = 0
        clearCandidateFrames()
        _uiState.update {
            it.copy(
                phase = EnrollmentPhase.NAME_ENTRY,
                currentAngleIndex = 0,
                capturedEmbeddings = emptyList(),
                consecutiveDetections = 0,
                faceDetected = false,
                statusMessage = null,
                guideState = FaceGuideState.NO_FACE,
                alignProgress = 0f,
                showCaptureFlash = false,
                qualityScore = 0f,
            )
        }
    }
}
