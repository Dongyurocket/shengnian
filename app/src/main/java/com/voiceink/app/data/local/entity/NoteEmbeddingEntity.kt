package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 向量以 FloatArray 序列化为 BLOB（个人量级暴力余弦检索足够，§4.1） */
@Entity(tableName = "note_embeddings")
data class NoteEmbeddingEntity(
    @PrimaryKey val noteId: Long,
    val vector: ByteArray,                  // FloatArray.toByteArray()
    val model: String,                      // 记录模型名，换模型后需重建
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean =
        other is NoteEmbeddingEntity && noteId == other.noteId &&
            vector.contentEquals(other.vector) && model == other.model

    override fun hashCode(): Int = 31 * noteId.hashCode() + vector.contentHashCode()
}
