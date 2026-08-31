package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.StopReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class AnthropicMessagesAdapterTest {

    private val server = MockWebServer()
    private val adapter = AnthropicMessagesAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun endpoint() = LlmEndpoint(
        server.url("/").toString().trimEnd('/'), "k", "m", LlmProtocol.ANTHROPIC_MESSAGES
    )

    @Test
    fun `请求头含 x-api-key 与 anthropic-version，末位为 assistant 预填`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"content":[{"type":"text","text":"\"intent\":\"note\"}"}],
                   "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}"""
            )
        )
        val r = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))

        // 预填拼接：响应文本拼回 '{'
        assertTrue(r.text.startsWith("{"))
        assertEquals("{\"intent\":\"note\"}", r.text)
        assertEquals(StopReason.COMPLETE, r.stopReason)
        assertEquals(15, r.usageTokens)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("k", req.getHeader("x-api-key"))
        assertEquals("2023-06-01", req.getHeader("anthropic-version"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"system\""))
        assertTrue(body.contains("\"max_tokens\""))
        // 末位 message 是 assistant 预填 '{'
        assertTrue(body.contains("\"role\":\"assistant\""))
    }

    @Test
    fun `stop_reason=max_tokens 识别为截断`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"content":[{"type":"text","text":"\"a\":"}],"stop_reason":"max_tokens"}"""
            )
        )
        val r = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        assertEquals(StopReason.MAX_TOKENS, r.stopReason)
    }

    @Test
    fun `401 映射为不可重试错误`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"invalid x-api-key"}}""")
        )
        val e = assertFailsWith<LlmException> {
            adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        }
        assertTrue(!e.retriable)
        assertEquals("invalid x-api-key", e.message)
    }
}
