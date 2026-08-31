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

    fun showTodoReminder(context: Context, todo: TodoEntity) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)

        val openApp = PendingIntent.getActivity(
            context, todo.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneAction = actionIntent(context, todo.id, ReminderReceiver.ACTION_COMPLETE, "完成")
        val snoozeAction = actionIntent(context, todo.id, ReminderReceiver.ACTION_SNOOZE, "延期 10 分钟")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("待办提醒")
            .setContentText(todo.content)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action(R.drawable.ic_notification, "完成", doneAction))
            .addAction(NotificationCompat.Action(R.drawable.ic_notification, "延期 10 分钟", snoozeAction))
            .build()
        nm.notify(todo.id.toInt(), notification)
    }

    private fun actionIntent(context: Context, todoId: Long, action: String, label: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_TODO_ID, todoId)
        val requestCode = (todoId.toInt() * 31) xor action.hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
