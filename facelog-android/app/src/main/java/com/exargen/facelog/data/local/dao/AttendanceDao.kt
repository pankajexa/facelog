package com.exargen.facelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exargen.facelog.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceEntity): Long

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 50): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecordsSync(limit: Int = 5): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_records WHERE person_id = :personId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRecordForPerson(personId: String): AttendanceEntity?

    @Query("SELECT * FROM attendance_records WHERE photo_path IS NOT NULL ORDER BY timestamp DESC")
    fun getRecordsWithPhotos(): Flow<List<AttendanceEntity>>

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAll()
}
