package com.exargen.facelog.ui.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.repository.AttendanceRepository
import com.exargen.facelog.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class PhotoItem(
    val photoPath: String,
    val personName: String,
    val attendanceType: String,
    val time: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
)

data class DateGroup(
    val dateLabel: String,
    val dateKey: Long,
    val photoCount: Int,
)

sealed class GalleryViewState {
    data class DateList(val dates: List<DateGroup>) : GalleryViewState()
    data class PhotoGrid(val dateLabel: String, val photos: List<PhotoItem>) : GalleryViewState()
}

@HiltViewModel
class PhotoGalleryViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val _viewState = MutableStateFlow<GalleryViewState>(GalleryViewState.DateList(emptyList()))
    val viewState: StateFlow<GalleryViewState> = _viewState.asStateFlow()

    private val istZone = TimeZone.getTimeZone("Asia/Kolkata")
    private var groupedByDate: Map<Long, List<PhotoItem>> = emptyMap()
    private var dateGroups: List<DateGroup> = emptyList()

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            attendanceRepository.getRecordsWithPhotos().collect { records ->
                val validRecords = records.filter { record ->
                    record.photoPath != null && File(record.photoPath).exists()
                }

                groupedByDate = validRecords
                    .map { record ->
                        PhotoItem(
                            photoPath = record.photoPath!!,
                            personName = record.personName,
                            attendanceType = record.attendanceType,
                            time = DateUtils.formatTime(record.timestamp),
                            timestamp = record.timestamp,
                            latitude = record.latitude,
                            longitude = record.longitude,
                            locationName = record.locationName,
                        )
                    }
                    .groupBy { startOfDay(it.timestamp) }

                dateGroups = groupedByDate.entries
                    .sortedByDescending { it.key }
                    .map { (dateKey, photos) ->
                        DateGroup(
                            dateLabel = DateUtils.formatDate(dateKey),
                            dateKey = dateKey,
                            photoCount = photos.size,
                        )
                    }

                // Update current view state with fresh data
                when (val current = _viewState.value) {
                    is GalleryViewState.DateList -> {
                        _viewState.value = GalleryViewState.DateList(dateGroups)
                    }
                    is GalleryViewState.PhotoGrid -> {
                        // Refresh the currently viewed date's photos
                        val matchingDateKey = dateGroups.find { it.dateLabel == current.dateLabel }?.dateKey
                        if (matchingDateKey != null) {
                            val photos = groupedByDate[matchingDateKey] ?: emptyList()
                            _viewState.value = GalleryViewState.PhotoGrid(current.dateLabel, photos)
                        } else {
                            _viewState.value = GalleryViewState.DateList(dateGroups)
                        }
                    }
                }
            }
        }
    }

    fun onDateSelected(dateGroup: DateGroup) {
        val photos = groupedByDate[dateGroup.dateKey] ?: emptyList()
        _viewState.value = GalleryViewState.PhotoGrid(dateGroup.dateLabel, photos)
    }

    /** Returns true if back was consumed (grid→list), false if already on list. */
    fun onBackPressed(): Boolean {
        return if (_viewState.value is GalleryViewState.PhotoGrid) {
            _viewState.value = GalleryViewState.DateList(dateGroups)
            true
        } else {
            false
        }
    }

    private fun startOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance(istZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
