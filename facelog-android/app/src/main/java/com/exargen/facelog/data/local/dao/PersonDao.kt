package com.exargen.facelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.exargen.facelog.data.local.entity.PersonEntity
import com.exargen.facelog.data.local.entity.PersonWithEmbeddings
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity)

    @Query("SELECT * FROM persons ORDER BY created_at DESC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :personId")
    suspend fun getById(personId: String): PersonEntity?

    @Query("SELECT COUNT(*) FROM persons")
    fun getPersonCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun getPersonCountSync(): Int

    @Transaction
    @Query("SELECT * FROM persons")
    suspend fun getAllPersonsWithEmbeddings(): List<PersonWithEmbeddings>

    @Query("DELETE FROM persons WHERE id = :personId")
    suspend fun deleteById(personId: String)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}
