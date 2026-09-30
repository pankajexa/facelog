package com.exargen.facelog.di

import android.content.Context
import androidx.room.Room
import com.exargen.facelog.data.local.FaceLogDatabase
import com.exargen.facelog.data.local.dao.AttendanceDao
import com.exargen.facelog.data.local.dao.FaceEmbeddingDao
import com.exargen.facelog.data.local.dao.PersonDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FaceLogDatabase {
        return Room.databaseBuilder(
            context,
            FaceLogDatabase::class.java,
            FaceLogDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePersonDao(database: FaceLogDatabase): PersonDao =
        database.personDao()

    @Provides
    fun provideFaceEmbeddingDao(database: FaceLogDatabase): FaceEmbeddingDao =
        database.faceEmbeddingDao()

    @Provides
    fun provideAttendanceDao(database: FaceLogDatabase): AttendanceDao =
        database.attendanceDao()
}
