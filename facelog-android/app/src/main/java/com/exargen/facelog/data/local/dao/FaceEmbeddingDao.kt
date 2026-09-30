package com.exargen.facelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exargen.facelog.data.local.entity.FaceEmbeddingEntity

@Dao
interface FaceEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: FaceEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<FaceEmbeddingEntity>)

    @Query("SELECT * FROM face_embeddings WHERE person_id = :personId")
    suspend fun getByPersonId(personId: String): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings")
    suspend fun getAll(): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_embeddings WHERE person_id = :personId")
    suspend fun deleteByPersonId(personId: String)
}
