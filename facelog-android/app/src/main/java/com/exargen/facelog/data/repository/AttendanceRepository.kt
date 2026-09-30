package com.exargen.facelog.data.repository

import com.exargen.facelog.data.local.dao.AttendanceDao
import com.exargen.facelog.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val attendanceDao: AttendanceDao,
) {
    suspend fun recordAttendance(record: AttendanceEntity): Long =
        attendanceDao.insert(record)

    fun getRecentRecords(limit: Int = 50): Flow<List<AttendanceEntity>> =
        attendanceDao.getRecentRecords(limit)

    suspend fun getRecentRecordsSync(limit: Int = 5): List<AttendanceEntity> =
        attendanceDao.getRecentRecordsSync(limit)

    suspend fun getLastRecordForPerson(personId: String): AttendanceEntity? =
        attendanceDao.getLastRecordForPerson(personId)

    fun getRecordsWithPhotos(): Flow<List<AttendanceEntity>> =
        attendanceDao.getRecordsWithPhotos()

    suspend fun deleteAll() =
        attendanceDao.deleteAll()
}
