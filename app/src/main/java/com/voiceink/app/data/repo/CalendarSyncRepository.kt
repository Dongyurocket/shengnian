package com.voiceink.app.data.repo

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.local.entity.TodoReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun sync(todo: TodoEntity, reminders: List<TodoReminderEntity>): Long = withContext(Dispatchers.IO) {
        check(hasWritePermission()) { "未授权日历权限，待办已保存但未同步日历" }
        val start = todo.deadline ?: reminders.firstOrNull()?.triggerAt
            ?: error("请先设置截止时间或提醒时间")
        val calendarId = writableCalendarId() ?: error("手机上没有可写入的日历")
        val reminderText = reminders.joinToString("\n") { reminder ->
            "提醒 ${reminder.sequence + 1}：${formatCalendarTime(reminder.triggerAt)}"
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, todo.content)
            put(CalendarContract.Events.DESCRIPTION, reminderText)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + EVENT_DURATION_MILLIS)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, 0)
        }
        val resolver = context.contentResolver
        val existingId = todo.calendarEventId
        if (existingId != null) {
            val updated = resolver.update(
                CalendarContract.Events.CONTENT_URI,
                values,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(existingId.toString())
            )
            if (updated > 0) return@withContext existingId
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
            ?: error("日历事件创建失败")
    }

    suspend fun delete(eventId: Long?) = withContext(Dispatchers.IO) {
        if (eventId == null || !hasWritePermission()) return@withContext
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events._ID} = ?",
            arrayOf(eventId.toString())
        )
    }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (cursor.moveToNext()) {
                if (cursor.getInt(accessIndex) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return cursor.getLong(idIndex)
                }
            }
        }
        return null
    }

    private fun formatCalendarTime(time: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(time)

    companion object {
        private const val EVENT_DURATION_MILLIS = 30 * 60 * 1000L
    }
}
