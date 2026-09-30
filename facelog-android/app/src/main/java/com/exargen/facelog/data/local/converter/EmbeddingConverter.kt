package com.exargen.facelog.data.local.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room TypeConverter for float array embeddings.
 * Stores as ByteArray for efficient storage.
 */
class EmbeddingConverter {

    @TypeConverter
    fun fromFloatArray(embedding: FloatArray?): ByteArray? {
        if (embedding == null) return null
        val buffer = ByteBuffer.allocate(embedding.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(embedding)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
