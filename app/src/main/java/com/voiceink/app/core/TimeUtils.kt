package com.voiceink.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun nowString(): String = fullFormat.format(Date())

    fun timeOfDay(ts: Long): String = timeFormat.format(Date(ts))

    /** 解析 AI 输出的 "yyyy-MM-dd HH:mm" 为 epoch millis；解析失败返回 null */
    fun parseDateTime(s: String): Long? =
        runCatching { fullFormat.parse(s.trim())?.time }.getOrNull()

    fun formatDateTime(ts: Long): String = fullFormat.format(Date(ts))

    private val fileFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
    fun formatForFile(ts: Long): String = fileFormat.format(Date(ts))

    /** 待办页头部："8 月 30 日 · 周六" */
    fun headerDate(now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val week = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return "${cal.get(Calendar.MONTH) + 1} 月 ${cal.get(Calendar.DAY_OF_MONTH)} 日 · ${week[cal.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    fun endOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /** 截止时间的口语化标签：今天 18:00 / 明天 09:30 / 周三 14:00 / 9月12日 10:00 */
    fun dueLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
        val time = timeOfDay(ts)
        val dayDiff = calendarDayDifference(now, ts)
        return when {
            dayDiff == 0 -> "今天 $time"
            dayDiff == 1 -> "明天 $time"
            dayDiff in 2..6 -> {
                val cal = Calendar.getInstance().apply { timeInMillis = ts }
                val week = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                "${week[cal.get(Calendar.DAY_OF_WEEK) - 1]} $time"
            }
            dayDiff > 6 -> {
                val cal = Calendar.getInstance().apply { timeInMillis = ts }
                "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 $time"
            }
            else -> "已过期"
        }
    }

    private fun calendarDayDifference(from: Long, to: Long): Int {
        val fromStart = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val toStart = Calendar.getInstance().apply {
            timeInMillis = to
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ((toStart.timeInMillis - fromStart.timeInMillis) / 86_400_000L).toInt()
    }

    /** 笔记流分组标签：今天 / 昨天 / 更早 */
    fun dayLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val nowYear = cal.get(Calendar.YEAR)
        val nowDay = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = ts
        val tsYear = cal.get(Calendar.YEAR)
        val tsDay = cal.get(Calendar.DAY_OF_YEAR)
        val diff = (nowYear - tsYear) * 365 + (nowDay - tsDay) // 跨年粗略即可，仅用于分组
        return when {
            diff <= 0 -> "今天"
            diff == 1 -> "昨天"
            else -> "更早"
        }
    }
}
