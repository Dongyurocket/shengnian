package com.voiceink.app.ai

import com.voiceink.app.ai.adapter.*
import com.voiceink.app.data.repo.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LlmGatewayStreamingTest {
    private val factory = mockk<LlmAdapterFactory>()
    private val settings = mockk<SettingsRepository>()
    private val adapter = mockk<LlmAdapter>()
    private val gateway = LlmGateway(factory, settings)
    private val request = LlmRequest("system", "user", "intent")
    private val endpoint = LlmEndpoint("https://example.invalid", "key", "model", LlmProtocol.OPENAI_RESPONSES)

    private fun configure(value: LlmEndpoint = endpoint) {
        coEvery { settings.currentEndpoint() } returns value
        every { factory.create(any()) } returns adapter
    }

    @Test fun `stream IO failure is retriable but gateway does not retry`() = runTest {
        configure()
        coEvery { adapter.completeStreaming(any(), any(), any()) } throws IOException("disconnected")
        val error = runCatching { gateway.completeStreaming(request) {} }.exceptionOrNull()
        assertTrue(error is LlmException && error.retriable)
        coVerify(exactly = 1) { adapter.completeStreaming(any(), any(), any()) }
    }

    @Test fun `gateway filters summaries unless all capability and opt in conditions hold`() = runTest {
        for (protocol in LlmProtocol.entries) {
            for (enabled in listOf(false, true)) {
                configure(endpoint.copy(protocol = protocol, showReasoningSummary = enabled, thinkingEnabled = true))
                coEvery { adapter.completeStreaming(any(), any(), any()) } coAnswers {
                    thirdArg<suspend (LlmStreamEvent) -> Unit>()(LlmStreamEvent.ReasoningSummaryDelta("summary"))
                    LlmResult("{}", StopReason.COMPLETE, null)
                }
                val events = mutableListOf<LlmStreamEvent>()
                gateway.completeStreaming(request, events::add)
                assertEquals(if (enabled && protocol == LlmProtocol.OPENAI_RESPONSES) 1 else 0, events.size)
            }
        }
    }

    @Test fun `cancellation remains cancellation`() = runTest {
        configure()
        coEvery { adapter.completeStreaming(any(), any(), any()) } throws CancellationException()
        assertTrue(runCatching { gateway.completeStreaming(request) {} }.exceptionOrNull() is CancellationException)
    }

    @Test fun `summary is disabled in default endpoint and stored config`() {
        assertFalse(endpoint.showReasoningSummary)
        assertFalse(SettingsRepository.LlmConfig().showReasoningSummary)
    }
}
