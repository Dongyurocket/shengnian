package com.voiceink.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 开机后对所有未完成、有待提醒时间的待办重排闹钟（§10） */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                todoRepository.pendingReminders()
                    .filter { (it.remindAt ?: 0L) > now }
                    .forEach { scheduler.schedule(it.id, it.remindAt!!) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
