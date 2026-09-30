package com.exargen.facelog.domain.model

data class AttendanceRecord(
    val id: String,
    val personId: String,
    val personName: String,
    val attendanceType: AttendanceType,
    val confidence: Float,
    val timestamp: Long,
)

enum class AttendanceType(val value: String) {
    CHECK_IN("CHECK_IN"),
    CHECK_OUT("CHECK_OUT");

    companion object {
        fun fromValue(value: String): AttendanceType =
            entries.find { it.value == value } ?: CHECK_IN
    }
}
