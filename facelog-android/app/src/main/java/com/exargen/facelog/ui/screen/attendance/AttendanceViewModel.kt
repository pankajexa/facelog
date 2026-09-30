package com.exargen.facelog.ui.screen.attendance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.location.LocationProvider
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.domain.model.RecognitionResult
import com.exargen.facelog.domain.usecase.AttendanceResult
import com.exargen.facelog.domain.usecase.RecognizeFaceUseCase
import com.exargen.facelog.domain.usecase.RecordAttendanceUseCase
import com.exargen.facelog.util.DateUtils
import com.exargen.facelog.util.safeCopy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Normalized bounding box (0-1 range, front-camera X-flipped) */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        fun fromRectF(rect: RectF, imageWidth: Int, imageHeight: Int, flipX: Boolean = true): NormalizedBox {
            val l = rect.left / imageWidth
            val t = rect.top / imageHeight
            val r = rect.right / imageWidth
            val b = rect.bottom / imageHeight
            return if (flipX) {
                NormalizedBox(1f - r, t, 1f - l, b)
            } else {
                NormalizedBox(l, t, r, b)
            }
        }
    }
}

sealed class ScanState {
    data object Idle : ScanState()

    data class Detecting(val box: NormalizedBox) : ScanState()

    data class Confirming(
        val box: NormalizedBox,
        val name: String,
        val matchCount: Int,
        val requiredMatches: Int,
    ) : ScanState()

    data class Success(
        val name: String,
        val label: String,
        val timestamp: String,
        val confidence: Float,
    ) : ScanState()

    data class Rejected(
        val message: String = "Face not recognized",
    ) : ScanState()

    data class Spoof(
        val message: String = "Spoof detected",
    ) : ScanState()

    data class AlreadyRecorded(
        val name: String,
        val label: String,
    ) : ScanState()
}

/** Haptic event types */
enum class HapticEvent {
    FACE_DETECTED,
    SUCCESS,
    NOT_RECOGNIZED,
    SPOOF,
    TAP_DISMISS,
}

data class AttendanceUiState(
    val scanState: ScanState = ScanState.Idle,
    val attendanceType: AttendanceType = AttendanceType.CHECK_IN,
    val scanCount: Int = 0,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizeFaceUseCase: RecognizeFaceUseCase,
    private val recordAttendanceUseCase: RecordAttendanceUseCase,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _hapticEvent = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 1)
    val hapticEvent: SharedFlow<HapticEvent> = _hapticEvent.asSharedFlow()

    companion object {
        private const val TAG = "AttendanceVM"
        private const val REQUIRED_CONSECUTIVE_MATCHES = 2
        private const val PROCESSED_PERSON_COOLDOWN_MS = 5_000L
    }

    private var resultDismissJob: Job? = null

    @Volatile
    private var isProcessingFrame = false

    // Short cache to prevent UI flickering from repeated scans of the same person
    private val processedPersons = mutableMapOf<String, Long>()

    // Multi-frame confirmation
    private var pendingPersonId: String? = null
    private var pendingPersonName: String? = null
    private var pendingSimilarity: Float = 0f
    private var consecutiveMatchCount: Int = 0
    private var pendingBox: NormalizedBox? = null
    private var pendingLivenessScore: Float = 1f
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var resultDisplayUntil: Long = 0

    private var hadFaceLastFrame: Boolean = false

    private val stampDateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    init {
        val type = savedStateHandle.get<String>("type") ?: "CHECK_IN"
        _uiState.update { it.copy(attendanceType = AttendanceType.fromValue(type)) }
    }

    fun onFrameAvailable(bitmap: Bitmap) {
        if (isProcessingFrame) return
        val currentState = _uiState.value.scanState
        if (currentState is ScanState.Spoof) return
        if (System.currentTimeMillis() < resultDisplayUntil) return

        // Copy bitmap early before launching coroutine to avoid camera buffer race
        val frameBitmap = bitmap.safeCopy() ?: return

        isProcessingFrame = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val outcome = recognizeFaceUseCase(frameBitmap)

                when (outcome) {
                    is RecognitionResult.Recognized -> handleRecognition(outcome, frameBitmap)
                    is RecognitionResult.SpoofDetected -> handleSpoof()
                    is RecognitionResult.NotRecognized -> {
                        resetPendingMatch()
                        _uiState.update {
                            it.copy(scanState = ScanState.Rejected())
                        }
                        _hapticEvent.tryEmit(HapticEvent.NOT_RECOGNIZED)
                        resultDisplayUntil = System.currentTimeMillis() + 1500
                        scheduleResultDismissal(1500)
                    }
                    is RecognitionResult.NoFaceDetected -> {
                        if (hadFaceLastFrame) {
                            resetPendingMatch()
                        }
                        hadFaceLastFrame = false
                        _uiState.update { it.copy(scanState = ScanState.Idle) }
                    }
                    is RecognitionResult.Error -> {
                        // Silent fail
                    }
                }
            } catch (_: Exception) {
                // Silent fail
            } finally {
                frameBitmap.recycle()
                isProcessingFrame = false
            }
        }
    }

    private fun resetPendingMatch() {
        pendingPersonId = null
        pendingPersonName = null
        pendingSimilarity = 0f
        consecutiveMatchCount = 0
        pendingBox = null
        pendingLivenessScore = 1f
        pendingBitmap?.recycle()
        pendingBitmap = null
    }

    private suspend fun handleRecognition(result: RecognitionResult.Recognized, frameBitmap: Bitmap) {
        val now = System.currentTimeMillis()
        val box = NormalizedBox.fromRectF(result.boundingBox, frameBitmap.width, frameBitmap.height)

        if (!hadFaceLastFrame) {
            _hapticEvent.tryEmit(HapticEvent.FACE_DETECTED)
        }
        hadFaceLastFrame = true

        // Short cache: prevent flickering for recently processed persons
        val lastProcessed = processedPersons[result.personId]
        if (lastProcessed != null && now - lastProcessed < PROCESSED_PERSON_COOLDOWN_MS) {
            val attendanceType = _uiState.value.attendanceType
            val label = if (attendanceType == AttendanceType.CHECK_IN) "Already checked in" else "Already checked out"
            _uiState.update {
                it.copy(scanState = ScanState.AlreadyRecorded(name = result.personName, label = label))
            }
            resultDisplayUntil = System.currentTimeMillis() + 1500
            scheduleResultDismissal(1500)
            return
        }

        // Multi-frame confirmation
        if (result.personId == pendingPersonId) {
            consecutiveMatchCount++
            pendingSimilarity = maxOf(pendingSimilarity, result.similarity)
            pendingBox = box
            pendingLivenessScore = result.livenessScore
            // Update pending bitmap to latest frame
            pendingBitmap?.recycle()
            pendingBitmap = frameBitmap.safeCopy(mutable = true)
            Log.d(TAG, "Consecutive match #$consecutiveMatchCount for ${result.personName} (sim=${result.similarity})")
        } else {
            pendingPersonId = result.personId
            pendingPersonName = result.personName
            pendingSimilarity = result.similarity
            consecutiveMatchCount = 1
            pendingBox = box
            pendingLivenessScore = result.livenessScore
            pendingBitmap?.recycle()
            pendingBitmap = frameBitmap.safeCopy(mutable = true)
            Log.d(TAG, "New candidate: ${result.personName} (sim=${result.similarity})")
        }

        if (consecutiveMatchCount < REQUIRED_CONSECUTIVE_MATCHES) {
            // Show confirming state
            _uiState.update {
                it.copy(
                    scanState = ScanState.Confirming(
                        box = box,
                        name = result.personName,
                        matchCount = consecutiveMatchCount,
                        requiredMatches = REQUIRED_CONSECUTIVE_MATCHES,
                    ),
                )
            }
            return
        }

        // Confirmed! Record attendance
        Log.d(TAG, "CONFIRMED: ${pendingPersonName} after $consecutiveMatchCount consecutive matches (best sim=${pendingSimilarity})")

        val attendanceType = _uiState.value.attendanceType
        val timestamp = now

        // Save photo with timestamp stamp
        val photoPath = saveAttendancePhoto(pendingPersonId!!, timestamp)

        val location = locationProvider.getLastLocation()

        val attendanceResult = recordAttendanceUseCase(
            personId = pendingPersonId!!,
            personName = pendingPersonName!!,
            attendanceType = attendanceType,
            confidence = pendingSimilarity,
            photoPath = photoPath,
            timestamp = timestamp,
            latitude = location?.latitude,
            longitude = location?.longitude,
            locationName = location?.locationName,
        )

        when (attendanceResult) {
            is AttendanceResult.Success -> {
                processedPersons[pendingPersonId!!] = now
                val label = if (attendanceType == AttendanceType.CHECK_IN) "CHECK IN" else "CHECK OUT"
                _uiState.update {
                    it.copy(
                        scanState = ScanState.Success(
                            name = pendingPersonName!!,
                            label = label,
                            timestamp = DateUtils.formatTime(now),
                            confidence = pendingSimilarity,
                        ),
                        scanCount = it.scanCount + 1,
                    )
                }
                _hapticEvent.tryEmit(HapticEvent.SUCCESS)
                resultDisplayUntil = System.currentTimeMillis() + 2500
                scheduleResultDismissal(2500)
            }
            is AttendanceResult.AlreadyCheckedIn -> {
                processedPersons[pendingPersonId!!] = now
                // Delete the photo we just saved since attendance wasn't recorded
                photoPath?.let { File(it).delete() }
                _uiState.update {
                    it.copy(scanState = ScanState.AlreadyRecorded(
                        name = pendingPersonName!!,
                        label = "Already checked in",
                    ))
                }
                resultDisplayUntil = System.currentTimeMillis() + 1500
                scheduleResultDismissal(1500)
            }
            is AttendanceResult.NotCheckedIn -> {
                processedPersons[pendingPersonId!!] = now
                photoPath?.let { File(it).delete() }
                _uiState.update {
                    it.copy(scanState = ScanState.AlreadyRecorded(
                        name = pendingPersonName!!,
                        label = "Not checked in yet",
                    ))
                }
                resultDisplayUntil = System.currentTimeMillis() + 1500
                scheduleResultDismissal(1500)
            }
            is AttendanceResult.Error -> {
                photoPath?.let { File(it).delete() }
                Log.e(TAG, "Attendance recording error", attendanceResult.exception)
            }
        }

        resetPendingMatch()
    }

    private fun saveAttendancePhoto(personId: String, timestamp: Long): String? {
        val bitmap = pendingBitmap ?: return null
        return try {
            // Create mutable copy and stamp date/time
            val mutableBitmap = bitmap.safeCopy(mutable = true) ?: run {
                Log.e(TAG, "Failed to create mutable copy of bitmap for photo")
                return null
            }

            val canvas = Canvas(mutableBitmap)
            val textSize = mutableBitmap.height * 0.04f // ~4% of image height
            val padding = mutableBitmap.height * 0.02f

            // Shadow paint
            val shadowPaint = Paint().apply {
                color = AndroidColor.BLACK
                this.textSize = textSize
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
                textAlign = Paint.Align.RIGHT
            }

            // Text paint
            val textPaint = Paint().apply {
                color = AndroidColor.WHITE
                this.textSize = textSize
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }

            val dateText = stampDateFormat.format(Date(timestamp))
            val x = mutableBitmap.width - padding
            val y = mutableBitmap.height - padding

            // Draw shadow then text
            canvas.drawText(dateText, x, y, shadowPaint)
            canvas.drawText(dateText, x, y, textPaint)

            // Save to file
            val dir = File(context.filesDir, "attendance")
            dir.mkdirs()
            val file = File(dir, "${personId}_${timestamp}.jpg")
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            mutableBitmap.recycle()

            // Validate file was actually written
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "Photo file validation failed: exists=${file.exists()}, size=${file.length()}")
                file.delete()
                return null
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save attendance photo", e)
            null
        }
    }

    private fun handleSpoof() {
        _uiState.update {
            it.copy(scanState = ScanState.Spoof())
        }
        _hapticEvent.tryEmit(HapticEvent.SPOOF)
        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(scanState = ScanState.Idle) }
        }
    }

    fun onDismissResult() {
        resultDismissJob?.cancel()
        resultDisplayUntil = 0
        _uiState.update { it.copy(scanState = ScanState.Idle) }
        _hapticEvent.tryEmit(HapticEvent.TAP_DISMISS)
    }

    private fun scheduleResultDismissal(delayMs: Long) {
        resultDismissJob?.cancel()
        resultDismissJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { it.copy(scanState = ScanState.Idle) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pendingBitmap?.recycle()
        pendingBitmap = null
    }
}
