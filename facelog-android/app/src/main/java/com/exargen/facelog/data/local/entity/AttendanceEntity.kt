package com.exargen.facelog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["person_id", "timestamp"]),
    ]
)
data class AttendanceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "person_id")
    val personId: String,

    @ColumnInfo(name = "person_name")
    val personName: String,

    @ColumnInfo(name = "attendance_type")
    val attendanceType: String, // "CHECK_IN" or "CHECK_OUT"

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "photo_path")
    val photoPath: String? = null,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "location_name")
    val locationName: String? = null,
)
