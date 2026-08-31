package com.voiceink.app.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.repo.TodoRepository
import com.voiceink.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repo: TodoRepository,
    private val reminder: ReminderScheduler
) : ViewModel() {

    val todos: StateFlow<List<TodoEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 勾选完成/取消完成：联动取消或恢复闹钟（§10） */
    fun toggleDone(todo: TodoEntity) {
        viewModelScope.launch {
            val done = !todo.done
            repo.setDone(todo.id, done)
            if (done) {
                reminder.cancel(todo.id)
            } else {
                todo.remindAt?.takeIf { it > System.currentTimeMillis() }
                    ?.let { reminder.schedule(todo.id, it) }
            }
        }
    }

    fun delete(todo: TodoEntity) {
        viewModelScope.launch {
            repo.delete(todo.id)
            reminder.cancel(todo.id)
        }
    }

    /** 编辑截止时间/提前量：重算并重排闹钟 */
    fun updateSchedule(id: Long, deadline: Long?, leadMinutes: Int) {
        viewModelScope.launch {
            repo.updateSchedule(id, deadline, leadMinutes)
            reminder.cancel(id)
            val remindAt = deadline?.let { it - leadMinutes * 60_000L }
            if (remindAt != null && remindAt > System.currentTimeMillis()) {
                reminder.schedule(id, remindAt)
            }
        }
    }

    fun canScheduleExact(): Boolean = reminder.canScheduleExact()
}
