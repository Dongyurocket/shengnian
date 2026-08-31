package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("category"), Index("createdAt"), Index("status")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",                 // AI 生成，未处理前为空
    val content: String,                    // 用户输入原文（先落库，AI 整理后可能被润色替换）
    val category: String? = null,           // 主题分类
    val type: String? = null,               // 灵感/总结/摘录/待研究/日记
    val mood: String? = null,               // 积极/中立/消极
    val summary: String? = null,
    val status: NoteStatus = NoteStatus.PENDING_AI,
    val source: String = "app",             // app / share / shortcut
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class NoteStatus { PENDING_AI, READY, AI_FAILED }
