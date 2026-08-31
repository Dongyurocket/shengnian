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
