package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 图片附件只保存应用私有文件路径，原始 content:// 授权不会进入后台任务。 */
@Entity(
    tableName = "note_attachments",
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val localPath: String,
    val mimeType: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)
