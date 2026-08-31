package com.voiceink.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voiceink.app.MainActivity
import com.voiceink.app.R
import com.voiceink.app.data.local.entity.TodoEntity

object NotificationHelper {

    private const val CHANNEL_SOUND = "todo_reminders"
    private const val CHANNEL_VIBRATE = "todo_reminders_vibrate"
    private const val CHANNEL_SILENT = "todo_reminders_silent"

    /**
     * 响铃 / 振动 / 静音三个渠道均为 IMPORTANCE_HIGH（保持抬头弹窗），仅声音与振动不同。
     * 渠道的声音振动配置由系统持有，切换模式时发向对应渠道即可。
     */
    private fun ensureChannel(context: Context, mode: ReminderMode): String {
        val channelId = when (mode) {
            ReminderMode.SOUND -> CHANNEL_SOUND
            ReminderMode.VIBRATE -> CHANNEL_VIBRATE
            ReminderMode.SILENT -> CHANNEL_SILENT
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(channelId) == null
        ) {
            val channel = when (mode) {
                ReminderMode.SOUND ->
                    NotificationChannel(channelId, "待办提醒", NotificationManager.IMPORTANCE_HIGH)
                ReminderMode.VIBRATE ->
                    NotificationChannel(channelId, "待办提醒（仅振动）", NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 200, 300)
                    }
                ReminderMode.SILENT ->
                    NotificationChannel(channelId, "待办提醒（静音）", NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
            }
            nm.createNotificationChannel(channel)
        }
        return channelId
    }

    fun showTodoReminder(
        context: Context,
        todo: TodoEntity,
        sequence: Int = 0,
        mode: ReminderMode = ReminderMode.SOUND
    ) {
        val channelId = ensureChannel(context, mode)
        val nm = context.getSystemService(NotificationManager::class.java)
        val notificationId = notificationId(todo.id, sequence)

        val openApp = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneAction = actionIntent(context, todo.id, sequence, ReminderReceiver.ACTION_COMPLETE)
        val snoozeAction = actionIntent(context, todo.id, sequence, ReminderReceiver.ACTION_SNOOZE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("待办提醒")
            .setContentText(todo.content)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action(R.drawable.ic_notification, "完成", doneAction))
            .addAction(NotificationCompat.Action(R.drawable.ic_notification, "延期 10 分钟", snoozeAction))
            .build()
        nm.notify(notificationId, notification)
    }

    fun notificationId(todoId: Long, sequence: Int): Int =
        if (sequence == 0) todoId.toInt() else (todoId * 31L + sequence).toInt()

    private fun actionIntent(context: Context, todoId: Long, sequence: Int, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_TODO_ID, todoId)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_SEQUENCE, sequence)
        val requestCode = notificationId(todoId, sequence) xor action.hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
