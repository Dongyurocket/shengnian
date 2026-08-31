package com.voiceink.app.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 到期发通知；通知上的「完成」「延期 10 分钟」两个 action 也在此处理（§10）。
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val todo = todoRepository.byId(todoId)
                        if (todo != null && !todo.done) {
                            NotificationHelper.showTodoReminder(context, todo)
                        }
                    }
                    ACTION_COMPLETE -> {
                        todoRepository.setDone(todoId, true)
                        scheduler.cancel(todoId)
                        dismissNotification(context, todoId)
                    }
                    ACTION_SNOOZE -> {
                        val triggerAt = System.currentTimeMillis() + 10 * 60_000L
                        scheduler.schedule(todoId, triggerAt)
                        dismissNotification(context, todoId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismissNotification(context: Context, todoId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(todoId.toInt())
    }

    companion object {
        const val ACTION_FIRE = "com.voiceink.app.reminder.FIRE"
        const val ACTION_COMPLETE = "com.voiceink.app.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.voiceink.app.reminder.SNOOZE"
        const val EXTRA_TODO_ID = "todoId"
    }
}
