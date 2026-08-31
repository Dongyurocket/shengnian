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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class OpenAiChatAdapterTest {

    private val server = MockWebServer()
    private val adapter = OpenAiChatAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun endpoint() = LlmEndpoint(
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "k", model = "m", protocol = LlmProtocol.OPENAI_CHAT
    )

    @Test
    fun `解析标准响应并断言请求构建正确`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"title\":\"测试\"}"},"finish_reason":"stop"}],
                   |"usage":{"total_tokens":42}}""".trimMargin()
            )
        )
        val result = adapter.complete(endpoint(), LlmRequest(system = "s", user = "u", jsonSchemaName = "intent"))

        assertEquals("{\"title\":\"测试\"}", result.text)
        assertEquals(StopReason.COMPLETE, result.stopReason)
        assertEquals(42, result.usageTokens)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer k", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"response_format\""))
        assertTrue(body.contains("\"max_tokens\""))
    }

    @Test
    fun `429 映射为可重试错误`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"error":{"message":"rate limited"}}""")
        )
        val e = assertFailsWith<LlmException> {
            adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        }
        assertTrue(e.retriable)
        assertEquals("rate limited", e.message)
    }

    @Test
    fun `finish_reason=length 识别为截断`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"title\":"},"finish_reason":"length"}]}"""
            )
        )
        val result = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        assertEquals(StopReason.MAX_TOKENS, result.stopReason)
    }

    @Test
    fun `baseUrl 自带 v1 时不重复拼接`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{}"},"finish_reason":"stop"}]}"""
            )
        )
        val ep = LlmEndpoint(
            server.url("/v1").toString().trimEnd('/'), "k", "m", LlmProtocol.OPENAI_CHAT
        )
        adapter.complete(ep, LlmRequest("s", "u", "intent"))
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun `400 报 max_completion_tokens 时自动换字段重试`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"Unsupported parameter: max_tokens. Use max_completion_tokens instead."}}""")
        )
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{}"},"finish_reason":"stop"}]}"""
            )
        )
        adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        server.takeRequest() // 丢弃首次请求
        val retried = server.takeRequest()
        assertTrue(retried.body.readUtf8().contains("\"max_completion_tokens\""))
    }

    @Test
    fun `400 报 response_format 不支持时降级去掉该字段`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"response_format is not supported by this model"}}""")
        )
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{}"},"finish_reason":"stop"}]}"""
            )
        )
        adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        server.takeRequest()
        val retried = server.takeRequest()
        assertFalse(retried.body.readUtf8().contains("response_format"))
    }
}
