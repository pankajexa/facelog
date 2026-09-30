package com.exargen.facelog.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PersonWithEmbeddings(
    @Embedded
    val person: PersonEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "person_id"
    )
    val embeddings: List<FaceEmbeddingEntity>,
)
