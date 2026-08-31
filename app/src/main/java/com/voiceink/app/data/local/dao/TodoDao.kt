package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.voiceink.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

data class NoteTodoCount(val noteId: Long, val openCount: Int)

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

    @Query("SELECT * FROM todos WHERE sourceNoteId = :noteId ORDER BY done ASC, createdAt ASC")
    fun observeForNote(noteId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE sourceNoteId = :noteId ORDER BY done ASC, createdAt ASC")
    suspend fun listForNote(noteId: Long): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE sourceNoteId = :noteId AND done = 0 AND content = :content LIMIT 1")
    suspend fun findOpenForNoteByContent(noteId: Long, content: String): TodoEntity?

    @Query("UPDATE todos SET sourceNoteId = NULL WHERE id = :id")
    suspend fun detachFromNote(id: Long)

    @Query("SELECT * FROM todos WHERE sourceNoteId = :noteId AND done = 0 ORDER BY createdAt ASC")
    suspend fun listOpenForNote(noteId: Long): List<TodoEntity>

    @Query("DELETE FROM todos WHERE sourceNoteId = :noteId AND done = 0")
    suspend fun deleteOpenForNote(noteId: Long)

    @Query("DELETE FROM todos WHERE sourceNoteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("SELECT sourceNoteId AS noteId, COUNT(*) AS openCount FROM todos WHERE sourceNoteId IS NOT NULL AND done = 0 GROUP BY sourceNoteId")
    fun observeOpenCountsPerNote(): Flow<List<NoteTodoCount>>

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todos SET deadline = :deadline, remindLeadMinutes = :lead, remindAt = :remindAt WHERE id = :id")
    suspend fun updateSchedule(id: Long, deadline: Long?, lead: Int, remindAt: Long?)

    @Query("SELECT * FROM todos WHERE done = 0 AND remindAt IS NOT NULL")
    suspend fun pendingReminders(): List<TodoEntity>
}
