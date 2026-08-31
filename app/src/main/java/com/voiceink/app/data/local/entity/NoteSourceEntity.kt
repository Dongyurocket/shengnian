package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object NoteSourceStatus {
    const val PENDING = "PENDING"
    const val READY = "READY"
    const val FAILED = "FAILED"
    const val UNSUPPORTED = "UNSUPPORTED"
}

/** 用户输入中发现的外部网页来源，与笔记语义关联表分开。 */
@Entity(
    tableName = "note_sources",
    indices = [Index(value = ["noteId", "url"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val url: String,
    val title: String = "",
    val excerpt: String = "",
    val status: String = NoteSourceStatus.PENDING,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
