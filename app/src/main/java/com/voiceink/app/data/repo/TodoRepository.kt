package com.voiceink.app.data.repo

import androidx.room.withTransaction
import com.voiceink.app.ai.prompt.ParsedIntent
import com.voiceink.app.data.local.AppDatabase
import com.voiceink.app.data.local.dao.TodoDao
import com.voiceink.app.data.local.dao.TodoReminderDao
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.local.entity.TodoReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val reminderDao: TodoReminderDao,
    private val database: AppDatabase,
    private val settings: SettingsRepository
) {
    fun observeAll(): Flow<List<TodoEntity>> = todoDao.observeAll()

    suspend fun byId(id: Long): TodoEntity? = todoDao.byId(id)

    suspend fun setDone(id: Long, done: Boolean) = todoDao.setDone(id, done)

    suspend fun delete(id: Long) {
        database.withTransaction {
            reminderDao.deleteForTodo(id)
            todoDao.deleteById(id)
        }
    }

    /** 开机重排数据源（§10 BootReceiver） */
    suspend fun pendingReminders(): List<TodoEntity> = todoDao.pendingReminders()

    fun observeReminders(todoId: Long): Flow<List<TodoReminderEntity>> =
        reminderDao.observeForTodo(todoId)

    fun observeAllReminders(): Flow<List<TodoReminderEntity>> = reminderDao.observeAll()

    suspend fun listReminders(todoId: Long): List<TodoReminderEntity> = reminderDao.listForTodo(todoId)

    suspend fun updateContent(id: Long, content: String) {
        todoDao.updateContent(id, content.trim())
    }

    /** 保存待办内容和完整的提醒时间表，第一条时间同步回旧 remindAt 字段。 */
    suspend fun updateDetails(
        id: Long,
        content: String,
        deadline: Long?,
        reminders: List<Long>,
        reminderCount: Int,
        reminderIntervalMinutes: Int,
        calendarEventId: Long?,
        remindLeadMinutes: Int = 0
    ) {
        val safeTimes = reminders
            .filter { it > System.currentTimeMillis() }
            .distinct()
            .sorted()
            .take(MAX_REMINDERS)
        val requestedCount = reminderCount.coerceIn(0, MAX_REMINDERS)
        val selectedTimes = safeTimes.take(requestedCount)
        val safeInterval = reminderIntervalMinutes.coerceIn(1, MAX_INTERVAL_MINUTES)
        val first = selectedTimes.firstOrNull()
        database.withTransaction {
            todoDao.updateDetails(
                id = id,
                content = content.trim(),
                deadline = deadline,
                remindAt = first,
                lead = remindLeadMinutes.coerceIn(0, 24 * 60),
                reminderCount = selectedTimes.size,
                reminderIntervalMinutes = safeInterval,
                calendarEventId = calendarEventId
            )
            reminderDao.deleteForTodo(id)
            if (selectedTimes.isNotEmpty()) {
                reminderDao.insertAll(
                    selectedTimes.mapIndexed { index, triggerAt ->
                        TodoReminderEntity(id, index, triggerAt)
                    }
                )
            }
        }
    }

    suspend fun updateReminderTrigger(todoId: Long, sequence: Int, triggerAt: Long) {
        val safeTrigger = triggerAt.takeIf { it > System.currentTimeMillis() } ?: return
        database.withTransaction {
            reminderDao.updateTrigger(todoId, sequence, safeTrigger)
            if (sequence == 0) todoDao.updateRemindAt(todoId, safeTrigger)
        }
    }

    suspend fun setCalendarEventId(id: Long, eventId: Long?) = todoDao.updateCalendarEventId(id, eventId)

    fun observeForNote(noteId: Long): Flow<List<TodoEntity>> = todoDao.observeForNote(noteId)

    suspend fun listForNote(noteId: Long): List<TodoEntity> = todoDao.listForNote(noteId)

    suspend fun deleteForNote(noteId: Long) = todoDao.deleteForNote(noteId)

    suspend fun clearOpenForNote(noteId: Long) {
        todoDao.deleteOpenForNote(noteId)
    }

    suspend fun findOpenForNoteByContent(noteId: Long, content: String): TodoEntity? =
        todoDao.findOpenForNoteByContent(noteId, content)

    suspend fun detachFromNote(id: Long) = todoDao.detachFromNote(id)

    fun observeOpenCountsPerNote(): Flow<Map<Long, Int>> =
        todoDao.observeOpenCountsPerNote().map { list -> list.associate { it.noteId to it.openCount } }

    /** 从 AI 解析结果建待办：remindAt = deadline - 提前量（用户未指定用默认）。sourceNoteId 可空。 */
    suspend fun insertFrom(parsed: ParsedIntent.Todo, sourceNoteId: Long?): Long {
        val lead = parsed.remindLeadMinutes ?: settings.remindLeadMinutes.first()
        val triggerAt = parsed.deadline
            ?.let { it - lead * 60_000L }
            ?.takeIf { it > System.currentTimeMillis() }
        return database.withTransaction {
            val todoId = todoDao.insert(
                TodoEntity(
                    content = parsed.content,
                    priority = parsed.priority,
                    deadline = parsed.deadline,
                    remindAt = triggerAt,
                    remindLeadMinutes = lead,
                    reminderCount = if (triggerAt == null) 0 else 1,
                    reminderIntervalMinutes = DEFAULT_INTERVAL_MINUTES,
                    sourceNoteId = sourceNoteId
                )
            )
            if (triggerAt != null) {
                reminderDao.insertAll(listOf(TodoReminderEntity(todoId, 0, triggerAt)))
            }
            todoId
        }
    }

    companion object {
        const val MAX_REMINDERS = 10
        const val MAX_INTERVAL_MINUTES = 7 * 24 * 60
        const val DEFAULT_INTERVAL_MINUTES = 10
    }
}
