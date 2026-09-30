package com.exargen.facelog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.exargen.facelog.data.local.converter.EmbeddingConverter
import com.exargen.facelog.data.local.dao.AttendanceDao
import com.exargen.facelog.data.local.dao.FaceEmbeddingDao
import com.exargen.facelog.data.local.dao.PersonDao
import com.exargen.facelog.data.local.entity.AttendanceEntity
import com.exargen.facelog.data.local.entity.FaceEmbeddingEntity
import com.exargen.facelog.data.local.entity.PersonEntity

@Database(
    entities = [
        PersonEntity::class,
        FaceEmbeddingEntity::class,
        AttendanceEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(EmbeddingConverter::class)
abstract class FaceLogDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        const val DATABASE_NAME = "facelog.db"
    }
}
