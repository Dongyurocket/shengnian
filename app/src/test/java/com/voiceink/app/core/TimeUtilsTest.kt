package com.voiceink.app.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun dueLabelShowsTomorrowForTheNextCalendarDay() {
        val now = date(2026, Calendar.AUGUST, 31, 21, 40)
        val tomorrow = date(2026, Calendar.SEPTEMBER, 1, 7, 50)

        assertEquals("明天 07:50", TimeUtils.dueLabel(tomorrow, now))
    }

    @Test
    fun dueLabelShowsTodayForTheSameCalendarDay() {
        val now = date(2026, Calendar.AUGUST, 31, 9, 40)
        val later = date(2026, Calendar.AUGUST, 31, 18, 0)

        assertEquals("今天 18:00", TimeUtils.dueLabel(later, now))
    }

    @Test
    fun dueLabelShowsExpiredForAnyPreviousCalendarDay() {
        val now = date(2026, Calendar.AUGUST, 31, 9, 40)
        val lastWeek = date(2026, Calendar.AUGUST, 24, 9, 0)

        assertEquals("已过期", TimeUtils.dueLabel(lastWeek, now))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
