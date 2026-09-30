package com.exargen.facelog.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.repository.AttendanceRepository
import com.exargen.facelog.data.repository.PersonRepository
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentActivity(
    val personName: String,
    val type: AttendanceType,
    val timestamp: Long,
)

data class HomeUiState(
    val enrolledCount: Int = 0,
    val maxPersons: Int = Constants.MAX_PERSONS,
    val recentActivity: List<RecentActivity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            personRepository.getPersonCount().collect { count ->
                _uiState.value = _uiState.value.copy(enrolledCount = count)
            }
        }
        loadRecentActivity()
    }

    private fun loadRecentActivity() {
        viewModelScope.launch {
            val records = attendanceRepository.getRecentRecordsSync(5)
            val activities = records.map { record ->
                RecentActivity(
                    personName = record.personName,
                    type = AttendanceType.fromValue(record.attendanceType),
                    timestamp = record.timestamp,
                )
            }
            _uiState.value = _uiState.value.copy(recentActivity = activities)
        }
    }

    fun refresh() {
        loadRecentActivity()
    }
}
