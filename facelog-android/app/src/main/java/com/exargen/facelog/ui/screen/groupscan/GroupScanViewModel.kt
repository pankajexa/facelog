package com.exargen.facelog.ui.screen.groupscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.location.LocationProvider
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.domain.model.RecognitionResult
import com.exargen.facelog.domain.usecase.AttendanceResult
import com.exargen.facelog.domain.usecase.RecognizeMultipleFacesUseCase
import com.exargen.facelog.domain.usecase.RecordAttendanceUseCase
import com.exargen.facelog.ui.screen.attendance.NormalizedBox
import com.exargen.facelog.util.DateUtils
import com.exargen.facelog.util.safeCopy
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Status of a tracked face in continuous group scan */
enum class FaceTrackStatus {
    DETECTING,
    CONFIRMING,
    CONFIRMED,
    UNKNOWN,
    SPOOF,
}

/** Immutable snapshot of a tracked face for the UI */
data class TrackedFace(
    val trackId: Int,
    val box: NormalizedBox,
    val status: FaceTrackStatus,
    val name: String?,
    val similarity: Float,
    val confirmProgress: Float, // 0..1 progress toward confirmation
    val attendanceRecorded: Boolean,
    val alreadyRecorded: Boolean,
)

/** A confirmed result for the session summary */
data class ConfirmedResult(
    val personId: String,
    val personName: String,
    val similarity: Float,
    val timestamp: String,
    val attendanceType: AttendanceType,
    val alreadyRecorded: Boolean,
)

data class GroupScanUiState(
    val attendanceType: AttendanceType = AttendanceType.CHECK_IN,
    val trackedFaces: List<TrackedFace> = emptyList(),
    val confirmedResults: List<ConfirmedResult> = emptyList(),
    val totalRecorded: Int = 0,
    val errorMessage: String? = null,
)

enum class GroupHapticEvent {
    FACE_CONFIRMED,
    ALREADY_RECORDED,
    SPOOF_DETECTED,
    ERROR,
}

@HiltViewModel
class GroupScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizeMultipleFacesUseCase: RecognizeMultipleFacesUseCase,
    private val recordAttendanceUseCase: RecordAttendanceUseCase,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "GroupScanVM"
        private const val REQUIRED_CONSECUTIVE_MATCHES = 2
        private const val MAX_MISSED_FRAMES = 4
        private const val IOU_THRESHOLD = 0.25f
        private const val UNKNOWN_THRESHOLD = 4
        private const val GROUP_RECOGNITION_THRESHOLD = 0.52f
    }

    private val _uiState = MutableStateFlow(GroupScanUiState())
    val uiState: StateFlow<GroupScanUiState> = _uiState.asStateFlow()

    private val _hapticEvent = MutableSharedFlow<GroupHapticEvent>(extraBufferCapacity = 1)
    val hapticEvent: SharedFlow<GroupHapticEvent> = _hapticEvent.asSharedFlow()

    @Volatile
    private var isProcessingFrame = false

    // Internal mutable tracking state
    private val tracks = mutableListOf<MutableTrack>()
    private var nextTrackId = 1
    private val confirmedResults = mutableListOf<ConfirmedResult>()

    private val stampDateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    private class MutableTrack(
        val trackId: Int,
        var box: NormalizedBox,
        var status: FaceTrackStatus = FaceTrackStatus.DETECTING,
        var candidatePersonId: String? = null,
        var candidateName: String? = null,
        var bestSimilarity: Float = 0f,
        var consecutiveMatches: Int = 0,
        var consecutiveUnknown: Int = 0,
        var missedFrames: Int = 0,
        var confirmedPersonId: String? = null,
        var confirmedName: String? = null,
        var attendanceRecorded: Boolean = false,
        var alreadyRecorded: Boolean = false,
    )

    init {
        val type = savedStateHandle.get<String>("type") ?: "CHECK_IN"
        _uiState.update { it.copy(attendanceType = AttendanceType.fromValue(type)) }
    }

    fun onPreviewFrame(bitmap: Bitmap) {
        if (isProcessingFrame) return
        isProcessingFrame = true

        // Copy bitmap to avoid race conditions with camera buffer
        val frameBitmap = bitmap.safeCopy() ?: return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                processFrame(frameBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error: ${e.message}", e)
            } finally {
                frameBitmap.recycle()
                isProcessingFrame = false
            }
        }
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        val multiFaceResult = recognizeMultipleFacesUseCase(
            bitmap = bitmap,
            recognitionThreshold = GROUP_RECOGNITION_THRESHOLD,
        )
        val now = System.currentTimeMillis()
        val attendanceType = _uiState.value.attendanceType

        // Convert results to (box, result) pairs
        val detectedFaces = multiFaceResult.results.mapNotNull { result ->
            val box = when (result) {
                is RecognitionResult.Recognized -> NormalizedBox.fromRectF(
                    result.boundingBox, multiFaceResult.imageWidth, multiFaceResult.imageHeight,
                )
                is RecognitionResult.NotRecognized -> NormalizedBox.fromRectF(
                    result.boundingBox, multiFaceResult.imageWidth, multiFaceResult.imageHeight,
                )
                is RecognitionResult.SpoofDetected -> NormalizedBox.fromRectF(
                    result.boundingBox, multiFaceResult.imageWidth, multiFaceResult.imageHeight,
                )
                else -> null
            }
            box?.let { it to result }
        }

        if (detectedFaces.isEmpty()) {
            // No faces — age all tracks
            for (track in tracks) {
                track.missedFrames++
            }
            tracks.removeAll { it.missedFrames > MAX_MISSED_FRAMES }
            publishState()
            return
        }

        // Greedy IoU matching: match detected faces to existing tracks
        val matchedTrackIndices = mutableSetOf<Int>()
        val matchedDetIndices = mutableSetOf<Int>()

        val iouPairs = mutableListOf<Triple<Int, Int, Float>>()
        for ((ti, track) in tracks.withIndex()) {
            for ((di, det) in detectedFaces.withIndex()) {
                val iou = computeIoU(track.box, det.first)
                if (iou > IOU_THRESHOLD) {
                    iouPairs.add(Triple(ti, di, iou))
                }
            }
        }
        iouPairs.sortByDescending { it.third }

        for ((ti, di, _) in iouPairs) {
            if (ti in matchedTrackIndices || di in matchedDetIndices) continue
            matchedTrackIndices.add(ti)
            matchedDetIndices.add(di)

            val track = tracks[ti]
            val (box, result) = detectedFaces[di]
            track.box = box
            track.missedFrames = 0

            // Already confirmed — just update position
            if (track.status == FaceTrackStatus.CONFIRMED) continue

            updateTrackWithResult(track, result, now, attendanceType, bitmap)
        }

        // Create new tracks for unmatched detections
        for ((di, det) in detectedFaces.withIndex()) {
            if (di in matchedDetIndices) continue
            val (box, result) = det
            val track = MutableTrack(trackId = nextTrackId++, box = box)
            initTrackWithResult(track, result)
            tracks.add(track)
        }

        // Age unmatched tracks
        for ((ti, track) in tracks.withIndex()) {
            if (ti !in matchedTrackIndices) {
                track.missedFrames++
            }
        }
        tracks.removeAll { it.missedFrames > MAX_MISSED_FRAMES }

        publishState()
    }

    private fun updateTrackWithResult(
        track: MutableTrack,
        result: RecognitionResult,
        now: Long,
        attendanceType: AttendanceType,
        frameBitmap: Bitmap,
    ) {
        when (result) {
            is RecognitionResult.Recognized -> {
                if (result.personId == track.candidatePersonId) {
                    track.consecutiveMatches++
                    track.bestSimilarity = maxOf(track.bestSimilarity, result.similarity)
                } else {
                    // Different person — reset
                    track.candidatePersonId = result.personId
                    track.candidateName = result.personName
                    track.bestSimilarity = result.similarity
                    track.consecutiveMatches = 1
                }
                track.consecutiveUnknown = 0
                track.status = FaceTrackStatus.CONFIRMING

                if (track.consecutiveMatches >= REQUIRED_CONSECUTIVE_MATCHES) {
                    confirmTrack(track, now, attendanceType, frameBitmap)
                }
            }
            is RecognitionResult.NotRecognized -> {
                track.consecutiveUnknown++
                if (track.status == FaceTrackStatus.CONFIRMING) {
                    track.candidatePersonId = null
                    track.candidateName = null
                    track.consecutiveMatches = 0
                    track.bestSimilarity = 0f
                }
                track.status = if (track.consecutiveUnknown >= UNKNOWN_THRESHOLD) {
                    FaceTrackStatus.UNKNOWN
                } else {
                    FaceTrackStatus.DETECTING
                }
            }
            is RecognitionResult.SpoofDetected -> {
                track.status = FaceTrackStatus.SPOOF
                track.candidatePersonId = null
                track.candidateName = null
                track.consecutiveMatches = 0
                _hapticEvent.tryEmit(GroupHapticEvent.SPOOF_DETECTED)
            }
            else -> { /* skip */ }
        }
    }

    private fun initTrackWithResult(track: MutableTrack, result: RecognitionResult) {
        when (result) {
            is RecognitionResult.Recognized -> {
                track.candidatePersonId = result.personId
                track.candidateName = result.personName
                track.bestSimilarity = result.similarity
                track.consecutiveMatches = 1
                track.status = FaceTrackStatus.CONFIRMING
            }
            is RecognitionResult.NotRecognized -> {
                track.consecutiveUnknown = 1
                track.status = FaceTrackStatus.DETECTING
            }
            is RecognitionResult.SpoofDetected -> {
                track.status = FaceTrackStatus.SPOOF
                _hapticEvent.tryEmit(GroupHapticEvent.SPOOF_DETECTED)
            }
            else -> { /* skip */ }
        }
    }

    private fun confirmTrack(track: MutableTrack, now: Long, attendanceType: AttendanceType, frameBitmap: Bitmap) {
        track.status = FaceTrackStatus.CONFIRMED
        track.confirmedPersonId = track.candidatePersonId
        track.confirmedName = track.candidateName

        val personId = track.candidatePersonId ?: return
        val personName = track.candidateName ?: return

        // Check if already confirmed in this session
        val alreadyInSession = confirmedResults.any { it.personId == personId && !it.alreadyRecorded }

        if (alreadyInSession) {
            track.alreadyRecorded = true
            track.attendanceRecorded = false
            _hapticEvent.tryEmit(GroupHapticEvent.ALREADY_RECORDED)
            Log.d(TAG, "CONFIRMED (already in session): $personName")
        } else {
            // Save photo synchronously while frameBitmap is still alive
            val photoPath = saveAttendancePhoto(frameBitmap, personId, now)

            viewModelScope.launch(Dispatchers.Default) {
                val location = locationProvider.getLastLocation()
                val result = recordAttendanceUseCase(
                    personId = personId,
                    personName = personName,
                    attendanceType = attendanceType,
                    confidence = track.bestSimilarity,
                    photoPath = photoPath,
                    timestamp = now,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    locationName = location?.locationName,
                )
                when (result) {
                    is AttendanceResult.Success -> {
                        track.attendanceRecorded = true
                        track.alreadyRecorded = false
                        confirmedResults.add(
                            ConfirmedResult(
                                personId = personId,
                                personName = personName,
                                similarity = track.bestSimilarity,
                                timestamp = DateUtils.formatTime(now),
                                attendanceType = attendanceType,
                                alreadyRecorded = false,
                            )
                        )
                        _hapticEvent.tryEmit(GroupHapticEvent.FACE_CONFIRMED)
                        Log.d(TAG, "CONFIRMED + RECORDED: $personName (sim=${track.bestSimilarity})")
                        publishState()
                    }
                    is AttendanceResult.AlreadyCheckedIn, is AttendanceResult.NotCheckedIn -> {
                        photoPath?.let { File(it).delete() }
                        track.alreadyRecorded = true
                        track.attendanceRecorded = false
                        confirmedResults.add(
                            ConfirmedResult(
                                personId = personId,
                                personName = personName,
                                similarity = track.bestSimilarity,
                                timestamp = DateUtils.formatTime(now),
                                attendanceType = attendanceType,
                                alreadyRecorded = true,
                            )
                        )
                        _hapticEvent.tryEmit(GroupHapticEvent.ALREADY_RECORDED)
                        Log.d(TAG, "CONFIRMED (alternation rejected): $personName")
                        publishState()
                    }
                    is AttendanceResult.Error -> {
                        photoPath?.let { File(it).delete() }
                        Log.e(TAG, "Attendance recording error for $personName", result.exception)
                        _hapticEvent.tryEmit(GroupHapticEvent.ERROR)
                    }
                }
            }
        }
    }

    private fun saveAttendancePhoto(bitmap: Bitmap, personId: String, timestamp: Long): String? {
        return try {
            val mutableBitmap = bitmap.safeCopy(mutable = true) ?: run {
                Log.e(TAG, "Failed to create mutable copy of bitmap for photo")
                return null
            }

            val canvas = Canvas(mutableBitmap)
            val textSize = mutableBitmap.height * 0.04f
            val padding = mutableBitmap.height * 0.02f

            val shadowPaint = Paint().apply {
                color = AndroidColor.BLACK
                this.textSize = textSize
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
                textAlign = Paint.Align.RIGHT
            }
            val textPaint = Paint().apply {
                color = AndroidColor.WHITE
                this.textSize = textSize
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }

            val dateText = stampDateFormat.format(Date(timestamp))
            val x = mutableBitmap.width - padding
            val y = mutableBitmap.height - padding
            canvas.drawText(dateText, x, y, shadowPaint)
            canvas.drawText(dateText, x, y, textPaint)

            val dir = File(context.filesDir, "attendance")
            dir.mkdirs()
            val file = File(dir, "${personId}_${timestamp}.jpg")
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            mutableBitmap.recycle()

            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "Photo file validation failed")
                file.delete()
                return null
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save attendance photo", e)
            null
        }
    }

    private fun publishState() {
        val trackedFacesUi = tracks.map { track ->
            TrackedFace(
                trackId = track.trackId,
                box = track.box,
                status = track.status,
                name = when (track.status) {
                    FaceTrackStatus.CONFIRMED -> track.confirmedName
                    FaceTrackStatus.CONFIRMING -> track.candidateName
                    else -> null
                },
                similarity = track.bestSimilarity,
                confirmProgress = track.consecutiveMatches.toFloat() / REQUIRED_CONSECUTIVE_MATCHES,
                attendanceRecorded = track.attendanceRecorded,
                alreadyRecorded = track.alreadyRecorded,
            )
        }
        _uiState.update {
            it.copy(
                trackedFaces = trackedFacesUi,
                confirmedResults = confirmedResults.toList(),
                totalRecorded = confirmedResults.count { r -> !r.alreadyRecorded },
            )
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun computeIoU(a: NormalizedBox, b: NormalizedBox): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)

        return interArea / (aArea + bArea - interArea)
    }
}
