package com.exargen.facelog.ui.screen.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exargen.facelog.data.repository.AttendanceRepository
import com.exargen.facelog.data.repository.PersonRepository
import com.exargen.facelog.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val enrolledCount: Int = 0,
    val maxPersons: Int = Constants.MAX_PERSONS,
    val recognitionThreshold: Float = Constants.DEFAULT_RECOGNITION_THRESHOLD,
    val appVersion: String = "1.0.0",
    val clearConfirmation: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val personRepository: PersonRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val threshold = prefs.getFloat("recognition_threshold", Constants.DEFAULT_RECOGNITION_THRESHOLD)
        _uiState.value = _uiState.value.copy(recognitionThreshold = threshold)

        viewModelScope.launch {
            personRepository.getPersonCount().collect { count ->
                _uiState.value = _uiState.value.copy(enrolledCount = count)
            }
        }
    }

    fun onThresholdChanged(threshold: Float) {
        _uiState.value = _uiState.value.copy(recognitionThreshold = threshold)
        prefs.edit().putFloat("recognition_threshold", threshold).apply()
    }

    fun showClearConfirmation() {
        _uiState.value = _uiState.value.copy(clearConfirmation = true)
    }

    fun dismissClearConfirmation() {
        _uiState.value = _uiState.value.copy(clearConfirmation = false)
    }

    fun clearAllData() {
        viewModelScope.launch {
            personRepository.deleteAllPersons()
            attendanceRepository.deleteAll()
            _uiState.value = _uiState.value.copy(clearConfirmation = false)
        }
    }
}
