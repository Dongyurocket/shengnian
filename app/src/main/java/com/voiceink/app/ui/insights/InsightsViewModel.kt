package com.voiceink.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class InsightsUiState(
    val weekNotes: Int = 0,
    val lastWeekNotes: Int = 0,
    val todosTotal: Int = 0,
    val todosDone: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val last14Days: List<Boolean> = List(14) { false },
    val hourBuckets: List<Int> = List(24) { 0 },
    val peakHour: Int = -1,
    val topTags: List<Pair<String, Int>> = emptyList()
)

/** 洞察页：全部数据来自 Room 聚合（§11.4），本地计算 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    noteDao: NoteDao,
    tagDao: TagDao,
    todoRepo: TodoRepository
) : ViewModel() {

    private val thirtyDaysAgo = System.currentTimeMillis() - 30L * 86_400_000

    val ui: StateFlow<InsightsUiState> = combine(
        noteDao.observeAllCreatedAt(),
        todoRepo.observeAll(),
        tagDao.observeTopTags(thirtyDaysAgo)
    ) { timestamps, todos, tags ->
        compute(timestamps, todos.size, todos.count { it.done }, tags.map { it.tag to it.cnt })
    }
        .mapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    private fun compute(
        timestamps: List<Long>,
        todosTotal: Int,
        todosDone: Int,
        topTags: List<Pair<String, Int>>
    ): InsightsUiState {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 86_400_000
        val twoWeeksAgo = now - 14L * 86_400_000

        val weekNotes = timestamps.count { it >= weekAgo }
        val lastWeekNotes = timestamps.count { it in twoWeeksAgo until weekAgo }

        // 日戳集合（归一到当天 0 点）
        val daySet = timestamps.map { dayStart(it) }.toSet()

        // 连续记录：从今天（或昨天）往前数
        var streak = 0
        var cursor = dayStart(now)
        if (cursor !in daySet) cursor -= 86_400_000L
        while (cursor in daySet) {
            streak++
            cursor -= 86_400_000L
        }

        // 最长连续
        val sorted = daySet.sorted()
        var longest = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in sorted) {
            run = if (d - prev == 86_400_000L) run + 1 else 1
            longest = maxOf(longest, run)
            prev = d
        }

        // 近 14 天点阵
        val today = dayStart(now)
        val last14 = (13 downTo 0).map { (today - it * 86_400_000L) in daySet }

        // 24 小时分布
        val buckets = IntArray(24)
        val cal = Calendar.getInstance()
        timestamps.forEach {
            cal.timeInMillis = it
            buckets[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        val peak = buckets.indices.maxByOrNull { buckets[it] } ?: -1

        return InsightsUiState(
            weekNotes = weekNotes,
            lastWeekNotes = lastWeekNotes,
            todosTotal = todosTotal,
            todosDone = todosDone,
            streak = streak,
            longestStreak = longest,
            last14Days = last14,
            hourBuckets = buckets.toList(),
            peakHour = if (buckets.any { it > 0 }) peak else -1,
            topTags = topTags
        )
    }

    private fun dayStart(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
