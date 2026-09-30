package com.exargen.facelog.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.repository.AttendanceRepository
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val _records = MutableStateFlow<List<HistoryRecordDisplay>>(emptyList())
    val records: StateFlow<List<HistoryRecordDisplay>> = _records.asStateFlow()

    init {
        loadRecords()
    }

    private fun loadRecords() {
        viewModelScope.launch {
            attendanceRepository.getRecentRecords(limit = 100).collect { attendanceRecords ->
                _records.value = attendanceRecords.map { record ->
                    val type = AttendanceType.fromValue(record.attendanceType)
                    HistoryRecordDisplay(
                        personName = record.personName,
                        type = type.value,
                        time = DateUtils.formatTime(record.timestamp),
                        date = DateUtils.formatDate(record.timestamp),
                        confidence = record.confidence,
                        photoPath = record.photoPath,
                        latitude = record.latitude,
                        longitude = record.longitude,
                        locationName = record.locationName,
                    )
                }
            }
        }
    }
}
