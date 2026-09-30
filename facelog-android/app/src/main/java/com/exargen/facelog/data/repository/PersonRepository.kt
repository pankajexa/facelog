package com.exargen.facelog.data.repository

import com.exargen.facelog.data.local.dao.FaceEmbeddingDao
import com.exargen.facelog.data.local.dao.PersonDao
import com.exargen.facelog.data.local.entity.FaceEmbeddingEntity
import com.exargen.facelog.data.local.entity.PersonEntity
import com.exargen.facelog.data.local.entity.PersonWithEmbeddings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(
    private val personDao: PersonDao,
    private val faceEmbeddingDao: FaceEmbeddingDao,
) {
    suspend fun insertPerson(person: PersonEntity) =
        personDao.insert(person)

    fun getAllPersons(): Flow<List<PersonEntity>> =
        personDao.getAllPersons()

    suspend fun getPersonById(id: String): PersonEntity? =
        personDao.getById(id)

    fun getPersonCount(): Flow<Int> =
        personDao.getPersonCount()

    suspend fun getPersonCountSync(): Int =
        personDao.getPersonCountSync()

    suspend fun getAllPersonsWithEmbeddings(): List<PersonWithEmbeddings> =
        personDao.getAllPersonsWithEmbeddings()

    suspend fun insertEmbeddings(embeddings: List<FaceEmbeddingEntity>) =
        faceEmbeddingDao.insertAll(embeddings)

    suspend fun getEmbeddingsForPerson(personId: String): List<FaceEmbeddingEntity> =
        faceEmbeddingDao.getByPersonId(personId)

    suspend fun deletePerson(personId: String) =
        personDao.deleteById(personId)

    suspend fun deleteAllPersons() {
        personDao.deleteAll()
    }
}
