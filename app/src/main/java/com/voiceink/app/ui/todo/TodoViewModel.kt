package com.voiceink.app.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.local.entity.TodoReminderEntity
import com.voiceink.app.data.repo.CalendarSyncRepository
import com.voiceink.app.data.repo.TodoRepository
import com.voiceink.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoEditInput(
    val content: String,
    val deadline: Long?,
    val reminders: List<Long>,
    val reminderCount: Int,
    val reminderIntervalMinutes: Int,
    val syncCalendar: Boolean
)

fun reminderTimesFrom(firstAt: Long, count: Int, intervalMinutes: Int): List<Long> {
    val safeCount = count.coerceIn(0, TodoRepository.MAX_REMINDERS)
    val safeInterval = intervalMinutes.coerceIn(1, TodoRepository.MAX_INTERVAL_MINUTES)
    return (0 until safeCount).map { index ->
        firstAt + index * safeInterval * 60_000L
    }
}

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repo: TodoRepository,
    private val reminder: ReminderScheduler,
    private val calendar: CalendarSyncRepository
) : ViewModel() {

    val todos: StateFlow<List<TodoEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remindersByTodo: StateFlow<Map<Long, List<TodoReminderEntity>>> = repo.observeAllReminders()
        .map { rows -> rows.groupBy { it.todoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 勾选完成/取消完成：联动取消或恢复全部提醒。 */
    fun toggleDone(todo: TodoEntity) {
        viewModelScope.launch {
            val done = !todo.done
            repo.setDone(todo.id, done)
            val reminders = repo.listReminders(todo.id)
            if (done) {
                reminders.forEach { reminder.cancel(todo.id, it.sequence) }
            } else {
                schedule(todo.copy(done = false), reminders)
            }
        }
    }

    fun delete(todo: TodoEntity) {
        viewModelScope.launch {
            repo.listReminders(todo.id).forEach { reminder.cancel(todo.id, it.sequence) }
            repo.delete(todo.id)
            calendar.delete(todo.calendarEventId)
        }
    }

    fun save(todo: TodoEntity, input: TodoEditInput, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.listReminders(todo.id).forEach { reminder.cancel(todo.id, it.sequence) }
                repo.updateDetails(
                    id = todo.id,
                    content = input.content,
                    deadline = input.deadline,
                    reminders = input.reminders,
                    reminderCount = input.reminderCount,
                    reminderIntervalMinutes = input.reminderIntervalMinutes,
                    calendarEventId = if (input.syncCalendar) todo.calendarEventId else null
                )
                val updated = repo.byId(todo.id)
                val reminders = repo.listReminders(todo.id)
                if (updated != null) schedule(updated, reminders)

                if (input.syncCalendar && updated != null) {
                    try {
                        val eventId = calendar.sync(updated, reminders)
                        repo.setCalendarEventId(todo.id, eventId)
                        _message.value = "待办已保存并同步到手机日历"
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _message.value = error.message ?: "待办已保存，但日历同步失败"
                    }
                } else {
                    if (todo.calendarEventId != null) calendar.delete(todo.calendarEventId)
                    repo.setCalendarEventId(todo.id, null)
                    _message.value = "待办已保存"
                }
                onDone()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _message.value = error.message ?: "保存待办失败"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun hasCalendarPermission(): Boolean = calendar.hasWritePermission()

    private fun schedule(todo: TodoEntity, reminders: List<TodoReminderEntity>) {
        if (todo.done) return
        val times = if (reminders.isEmpty()) {
            todo.remindAt?.let { listOf(TodoReminderEntity(todo.id, 0, it)) }.orEmpty()
        } else {
            reminders
        }
        times.filter { it.triggerAt > System.currentTimeMillis() }.forEach { item ->
            reminder.schedule(todo.id, item.sequence, item.triggerAt)
        }
    }

    fun canScheduleExact(): Boolean = reminder.canScheduleExact()
}
