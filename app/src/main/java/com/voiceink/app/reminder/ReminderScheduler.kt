package com.voiceink.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** AlarmManager 封装（§10）：API 31+ 未授权精确闹钟时降级 setWindow */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun schedule(todoId: Long, triggerAt: Long) {
        schedule(todoId, sequence = 0, triggerAt = triggerAt)
    }

    fun schedule(todoId: Long, sequence: Int, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        val pi = pendingIntent(todoId, sequence)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // 未授权精确闹钟 → 降级窗口闹钟（设置页/UI 引导用户授权）
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(todoId: Long, sequence: Int = 0) =
        am.cancel(pendingIntent(todoId, sequence))

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun pendingIntent(todoId: Long, sequence: Int) = PendingIntent.getBroadcast(
        context,
        requestCode(todoId, sequence),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TODO_ID, todoId)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_SEQUENCE, sequence),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun requestCode(todoId: Long, sequence: Int): Int =
        if (sequence == 0) todoId.toInt() else (todoId * 31L + sequence).toInt()
}
