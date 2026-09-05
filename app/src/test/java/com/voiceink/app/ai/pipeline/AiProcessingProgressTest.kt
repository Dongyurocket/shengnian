package com.voiceink.app.ai.pipeline

import androidx.work.Data
import androidx.work.WorkInfo
import com.voiceink.app.ai.adapter.LlmStreamEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AiProcessingProgressTest {
    @Test fun `summary store is bounded isolated by worker and cleared independently`() {
        val store = AiSummaryStore()
        store.set("old", "a".repeat(2000))
        store.set("new", "fresh")
        assertEquals(600, store.summaries.value["old"]!!.length)
        store.clear("old")
        assertEquals(mapOf("new" to "fresh"), store.summaries.value)
        store.set("new", "")
        assertTrue(store.summaries.value.isEmpty())
    }

    @Test fun `stage is deduplicated and a new connection clears old summary`() = runTest {
        val updates = mutableListOf<AiProgress>()
        val progress = AiStreamProgress(updates::add)
        progress.onEvent(LlmStreamEvent.Connected)
        progress.onEvent(LlmStreamEvent.ReasoningSummaryDelta("summary"))
        repeat(20) { progress.onEvent(LlmStreamEvent.TextDelta("x")) }
        assertEquals(3, updates.size)
        assertEquals(AiPhase.GENERATING, updates.last().phase)
        progress.onEvent(LlmStreamEvent.Connected)
        assertEquals(AiProgress(AiPhase.ANALYZING), updates.last())
    }

    private fun info(state: WorkInfo.State, attempts: Int = 0, phase: String? = null) = mockk<WorkInfo> {
        every { this@mockk.state } returns state
        every { runAttemptCount } returns attempts
        every { progress } returns mockk<Data> {
            every { getString(AiProcessWorker.KEY_PHASE) } returns phase
        }
    }

    @Test fun `queued retry ignores stale progress and summary`() {
        val progress = progressForWork(info(WorkInfo.State.ENQUEUED, 1, "GENERATING"), "stale")
        assertEquals(AiProgress(AiPhase.RETRYING), progress)
    }

    @Test fun `running worker restores its phase with only the in memory summary`() {
        assertEquals(AiProgress(AiPhase.ANALYZING, "summary"),
            progressForWork(info(WorkInfo.State.RUNNING, phase = "ANALYZING"), "summary"))
        assertEquals(AiProgress(AiPhase.GENERATING),
            progressForWork(info(WorkInfo.State.RUNNING, phase = "GENERATING"), ""))
    }

    @Test fun `unknown phase and no worker have safe initial states`() {
        assertEquals(AiProgress(), progressForWork(null, "stale"))
        assertEquals(AiPhase.PREPARING, progressForWork(info(WorkInfo.State.RUNNING, phase = "unknown"), "").phase)
        assertEquals(AiPhase.QUEUED, progressForWork(info(WorkInfo.State.ENQUEUED), "stale").phase)
    }

    @Test fun `finished and cancelled workers do not display summaries`() {
        for (state in listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
            assertEquals("", progressForWork(info(state), "old").reasoningSummary)
        }
        assertEquals(AiPhase.CANCELLED, progressForWork(info(WorkInfo.State.CANCELLED), "").phase)
    }
}
