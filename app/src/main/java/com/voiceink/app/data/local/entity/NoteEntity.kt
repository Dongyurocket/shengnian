package com.voiceink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("category"), Index("createdAt"), Index("status"), Index("lifecycleStatus")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",                 // AI 生成，未处理前为空
    val content: String,                    // 当前展示正文
    @ColumnInfo(defaultValue = "''")
    val rawContent: String = content,       // 最近一次用户输入，供二次整理使用
    val category: String? = null,           // 主题分类
    val type: String? = null,               // 灵感/总结/摘录/待研究/日记
    val mood: String? = null,               // 积极/中立/消极
    val summary: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isInspiration: Boolean = false,     // AI 判断的灵感标记
    val status: NoteStatus = NoteStatus.PENDING_AI,
    @ColumnInfo(defaultValue = "'PENDING'")
    val lifecycleStatus: NoteLifecycleStatus = NoteLifecycleStatus.PENDING,
    val source: String = "app",             // app / share / shortcut
    val intentHint: String? = null,         // 入口预设意图（如快捷方式"新建待办"），AI 分流时参考
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class NoteStatus { PENDING_AI, READY, AI_FAILED }

enum class NoteLifecycleStatus(val label: String) {
    PENDING("待完成"),
    COMPLETED("已实现"),
    ABANDONED("已废弃")
}
