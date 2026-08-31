package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "todo_reminders",
    primaryKeys = ["todoId", "sequence"],
    indices = [Index("triggerAt")],
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TodoReminderEntity(
    val todoId: Long,
    val sequence: Int,
    val triggerAt: Long
)
