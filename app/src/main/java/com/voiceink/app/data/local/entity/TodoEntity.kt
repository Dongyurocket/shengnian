package com.voiceink.app.data.local.entity

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "1")
    val reminderCount: Int = 1,
    @ColumnInfo(defaultValue = "10")
    val reminderIntervalMinutes: Int = 10,
    val calendarEventId: Long? = null,       // 已同步到手机日历的事件 ID
    val done: Boolean = false,
    val sourceNoteId: Long? = null,         // 溯源：来自哪条输入
    val createdAt: Long = System.currentTimeMillis()
)
