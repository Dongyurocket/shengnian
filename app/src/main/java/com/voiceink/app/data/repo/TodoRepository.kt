package com.voiceink.app.data.repo

import com.voiceink.app.ai.prompt.ParsedIntent
import com.voiceink.app.data.local.dao.TodoDao
import com.voiceink.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val settings: SettingsRepository
) {
    fun observeAll(): Flow<List<TodoEntity>> = todoDao.observeAll()

    suspend fun byId(id: Long): TodoEntity? = todoDao.byId(id)

    suspend fun setDone(id: Long, done: Boolean) = todoDao.setDone(id, done)

    /** 从 AI 解析结果建待办：remindAt = deadline - 提前量（用户未指定用默认） */
    suspend fun insertFrom(parsed: ParsedIntent.Todo, sourceNoteId: Long): Long {
        val lead = parsed.remindLeadMinutes ?: settings.remindLeadMinutes.first()
        val remindAt = parsed.deadline
            ?.let { it - lead * 60_000L }
            ?.takeIf { it > System.currentTimeMillis() }
        return todoDao.insert(
            TodoEntity(
                content = parsed.content,
                priority = parsed.priority,
                deadline = parsed.deadline,
                remindAt = remindAt,
                remindLeadMinutes = lead,
                sourceNoteId = sourceNoteId
            )
        )
    }
}
