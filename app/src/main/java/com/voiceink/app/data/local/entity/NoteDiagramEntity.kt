package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** AI 生成的已验证图表规格；specJson 只保存数据，不保存可执行代码。 */
@Entity(
    tableName = "note_diagrams",
    indices = [Index("noteId"), Index(value = ["noteId", "kind"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteDiagramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val kind: String,
    val title: String,
    val specJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
