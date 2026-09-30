package com.exargen.facelog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["person_id"])]
)
data class FaceEmbeddingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "person_id")
    val personId: String,

    @ColumnInfo(name = "embedding")
    val embedding: FloatArray,

    @ColumnInfo(name = "angle_label")
    val angleLabel: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbeddingEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
