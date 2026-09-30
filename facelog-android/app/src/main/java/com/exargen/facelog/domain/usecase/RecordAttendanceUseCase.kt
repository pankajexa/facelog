package com.exargen.facelog.domain.usecase

import com.exargen.facelog.data.local.entity.AttendanceEntity
import com.exargen.facelog.data.repository.AttendanceRepository
import com.exargen.facelog.domain.model.AttendanceType
import javax.inject.Inject

sealed class AttendanceResult {
    data class Success(val record: AttendanceEntity) : AttendanceResult()
    data class AlreadyCheckedIn(val personName: String) : AttendanceResult()
    data class NotCheckedIn(val personName: String) : AttendanceResult()
    data class Error(val exception: Exception) : AttendanceResult()
}

class RecordAttendanceUseCase @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
) {
    suspend operator fun invoke(
        personId: String,
        personName: String,
        attendanceType: AttendanceType,
        confidence: Float,
        photoPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String? = null,
    ): AttendanceResult {
        return try {
            val lastRecord = attendanceRepository.getLastRecordForPerson(personId)

            // Enforce strict alternation
            when (attendanceType) {
                AttendanceType.CHECK_IN -> {
                    if (lastRecord != null && lastRecord.attendanceType == AttendanceType.CHECK_IN.value) {
                        return AttendanceResult.AlreadyCheckedIn(personName)
                    }
                }
                AttendanceType.CHECK_OUT -> {
                    if (lastRecord == null || lastRecord.attendanceType == AttendanceType.CHECK_OUT.value) {
                        return AttendanceResult.NotCheckedIn(personName)
                    }
                }
            }

            val record = AttendanceEntity(
                personId = personId,
                personName = personName,
                attendanceType = attendanceType.value,
                confidence = confidence,
                timestamp = timestamp,
                photoPath = photoPath,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
            )

            attendanceRepository.recordAttendance(record)
            AttendanceResult.Success(record)
        } catch (e: Exception) {
            AttendanceResult.Error(e)
        }
    }
}
