package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.voiceink.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun byId(id: Long): TodoEntity?

    @Query("SELECT * FROM todos ORDER BY done ASC, deadline IS NULL ASC, deadline ASC, priority DESC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("SELECT COUNT(*) FROM todos WHERE sourceNoteId = :noteId AND done = 0")
    fun observeOpenCountForNote(noteId: Long): Flow<Int>

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todos SET deadline = :deadline, remindLeadMinutes = :lead, remindAt = :remindAt WHERE id = :id")
    suspend fun updateSchedule(id: Long, deadline: Long?, lead: Int, remindAt: Long?)

    @Query("SELECT * FROM todos WHERE done = 0 AND remindAt IS NOT NULL")
    suspend fun pendingReminders(): List<TodoEntity>
}
