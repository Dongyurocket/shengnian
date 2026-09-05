package com.voiceink.app.ai.pipeline

import android.content.Context
import com.voiceink.app.ai.*
import com.voiceink.app.ai.adapter.LlmException
import com.voiceink.app.ai.adapter.LlmStreamEvent
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.repo.*
import com.voiceink.app.reminder.ReminderScheduler
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiPipelineStreamingTest {
    private val gateway = mockk<LlmGateway>()
    private val notes = mockk<NoteRepository>(relaxed = true)
    private val attachments = mockk<NoteAttachmentRepository>(relaxed = true)
    private val sources = mockk<NoteSourceRepository>(relaxed = true)
    private val todos = mockk<TodoRepository>(relaxed = true)
    private val links = mockk<LinkDiscovery>(relaxed = true)
    private val reminder = mockk<ReminderScheduler>(relaxed = true)
    private val note = NoteEntity(id = 1, content = "original", updatedAt = 100)
    private val pipeline = AiPipeline(mockk<Context>(), gateway, notes, attachments, sources, todos, links, reminder)
    private val organizedJson = """{"intent":"note","title":"Organized","content":"Result","tags":[],"todos":[]}"""

    @Before fun setUp() {
        coEvery { notes.byId(1) } returns note
        coEvery { notes.markFailedIfCurrent(note) } returns true
        coEvery { notes.applyOrganization(any(), any(), any()) } returns true
    }

    private fun responds(text: String = organizedJson, stop: StopReason = StopReason.COMPLETE) {
        coEvery { gateway.completeStreaming(any(), any()) } coAnswers {
            val emit = secondArg<suspend (LlmStreamEvent) -> Unit>()
            emit(LlmStreamEvent.Connected)
            emit(LlmStreamEvent.TextDelta("{"))
            coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
            LlmResult(text, stop, 20)
        }
    }

    @Test fun `only completed JSON is applied after connecting analysis and generation`() = runTest {
        responds()
        val progress = mutableListOf<AiProgress>()
        assertEquals(AiPipeline.Outcome.Done, pipeline.process(1, onProgress = progress::add))
        assertEquals(listOf(AiPhase.PREPARING, AiPhase.CONNECTING, AiPhase.ANALYZING, AiPhase.GENERATING, AiPhase.SAVING), progress.map { it.phase })
        coVerify(exactly = 1) { notes.applyOrganization(1, match { it.title == "Organized" && it.content == "Result" }, note) }
        coVerify(exactly = 0) { gateway.complete(any()) }
    }

    @Test fun `partial JSON then disconnected stream never writes a result or a premature failure`() = runTest {
        coEvery { gateway.completeStreaming(any(), any()) } coAnswers {
            secondArg<suspend (LlmStreamEvent) -> Unit>()(LlmStreamEvent.TextDelta(organizedJson))
            throw LlmException(-1, "disconnected", true)
        }
        assertEquals(AiPipeline.Outcome.Retryable, pipeline.process(1))
        coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
        coVerify(exactly = 0) { notes.markFailedIfCurrent(any()) }
        coVerify(exactly = 0) { todos.insertFrom(any(), any()) }
    }

    @Test fun `last retry marks only the original snapshot failed`() = runTest {
        coEvery { gateway.completeStreaming(any(), any()) } throws LlmException(-1, "disconnected", true)
        assertEquals(AiPipeline.Outcome.Fatal, pipeline.process(1, finalAttempt = true))
        coVerify(exactly = 1) { notes.markFailedIfCurrent(note) }
    }

    @Test fun `user edit during stream is not overwritten or marked failed`() = runTest {
        coEvery { gateway.completeStreaming(any(), any()) } coAnswers {
            coEvery { notes.byId(1) } returns note.copy(content = "edited", updatedAt = 200)
            throw LlmException(-1, "disconnected", true)
        }
        assertEquals(AiPipeline.Outcome.Done, pipeline.process(1, finalAttempt = true))
        coVerify(exactly = 0) { notes.markFailedIfCurrent(any()) }
        coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
    }

    @Test fun `max tokens and invalid JSON remain pending until final retry`() = runTest {
        responds(stop = StopReason.MAX_TOKENS)
        assertEquals(AiPipeline.Outcome.Retryable, pipeline.process(1))
        responds(text = "not JSON")
        assertEquals(AiPipeline.Outcome.Retryable, pipeline.process(1))
        coVerify(exactly = 0) { notes.markFailedIfCurrent(any()) }
        coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
    }

    @Test fun `fatal stream error marks failure without retrying`() = runTest {
        coEvery { gateway.completeStreaming(any(), any()) } throws LlmException(401, "unauthorized", false)
        assertEquals(AiPipeline.Outcome.Fatal, pipeline.process(1))
        coVerify(exactly = 1) { notes.markFailedIfCurrent(note) }
    }

    @Test fun `cancellation is propagated without writes`() = runTest {
        coEvery { gateway.completeStreaming(any(), any()) } throws CancellationException()
        assertTrue(runCatching { pipeline.process(1) }.exceptionOrNull() is CancellationException)
        coVerify(exactly = 0) { notes.markFailedIfCurrent(any()) }
        coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
    }

    @Test fun `refused or incomplete output cannot enter JSON parser and persistence`() = runTest {
        responds(stop = StopReason.OTHER)
        assertEquals(AiPipeline.Outcome.Fatal, pipeline.process(1))
        coVerify(exactly = 0) { notes.applyOrganization(any(), any(), any()) }
    }
}
