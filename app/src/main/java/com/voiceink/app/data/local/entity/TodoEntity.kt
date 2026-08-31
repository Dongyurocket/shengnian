package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "todos", indices = [Index("deadline"), Index("done")])
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val priority: Int = 1,                  // 0低 1中 2高，AI 建议、用户可改
    val deadline: Long? = null,
    val remindAt: Long? = null,             // = deadline - leadMinutes（或用户单独设定）
    val remindLeadMinutes: Int = 5,         // 默认提前 5 分钟
    val done: Boolean = false,
    val sourceNoteId: Long? = null,         // 溯源：来自哪条输入
    val createdAt: Long = System.currentTimeMillis()
)
