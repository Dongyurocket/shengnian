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

    private const val CHANNEL_ID = "todo_reminders"

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun showTodoReminder(context: Context, todo: TodoEntity, sequence: Int = 0) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val notificationId = notificationId(todo.id, sequence)

        val openApp = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneAction = actionIntent(context, todo.id, sequence, ReminderReceiver.ACTION_COMPLETE)
        val snoozeAction = actionIntent(context, todo.id, sequence, ReminderReceiver.ACTION_SNOOZE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (todo.isAlarm) "闹钟" else "待办提醒")
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
