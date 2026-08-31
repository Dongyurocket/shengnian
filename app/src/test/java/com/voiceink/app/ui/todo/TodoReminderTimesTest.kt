package com.voiceink.app.ui.todo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoReminderTimesTest {
    @Test
    fun generatesBoundedReminderTimesAtTheRequestedInterval() {
        val first = 1_000_000L
        val result = reminderTimesFrom(first, 3, 15)

        assertEquals(
            listOf(first, first + 15 * 60_000L, first + 30 * 60_000L),
            result
        )
    }

    @Test
    fun clampsReminderCountToTheProductLimit() {
        assertEquals(10, reminderTimesFrom(1_000_000L, 99, 1).size)
    }
}
