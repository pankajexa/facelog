package com.exargen.facelog.domain.model

data class Person(
    val id: String,
    val name: String,
    val photoPath: String?,
    val embeddingCount: Int,
    val createdAt: Long,
)
