package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 双向链接（§4.1）：建立时写入两行 (a→b, b→a)，删除同理。
 */
@Entity(
    tableName = "note_links",
    primaryKeys = ["fromId", "toId"],
    indices = [Index("toId")],
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["fromId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NoteEntity::class, ["id"], ["toId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class NoteLinkEntity(
    val fromId: Long,
    val toId: Long,
    val score: Float,                       // 综合相似度 0..1
    val reason: String? = null,             // LLM 给出的关联理由，详情页展示
    val autoCreated: Boolean = true,
    val confirmed: Boolean = false,         // 用户确认过 → 权重提升
    val createdAt: Long = System.currentTimeMillis()
)
