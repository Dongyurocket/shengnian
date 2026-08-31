package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.TodoReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<TodoReminderEntity>)

    @Query("SELECT * FROM todo_reminders WHERE todoId = :todoId ORDER BY sequence ASC")
    fun observeForTodo(todoId: Long): Flow<List<TodoReminderEntity>>

    @Query("SELECT * FROM todo_reminders WHERE todoId = :todoId ORDER BY sequence ASC")
    suspend fun listForTodo(todoId: Long): List<TodoReminderEntity>

    @Query("SELECT * FROM todo_reminders ORDER BY triggerAt ASC")
    fun observeAll(): Flow<List<TodoReminderEntity>>

    @Query("DELETE FROM todo_reminders WHERE todoId = :todoId")
    suspend fun deleteForTodo(todoId: Long)

    @Query("UPDATE todo_reminders SET triggerAt = :triggerAt WHERE todoId = :todoId AND sequence = :sequence")
    suspend fun updateTrigger(todoId: Long, sequence: Int, triggerAt: Long)
}
