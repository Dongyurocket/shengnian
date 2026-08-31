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

class OpenAiResponsesAdapterTest {

    private val server = MockWebServer()
    private val adapter = OpenAiResponsesAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun endpoint() = LlmEndpoint(
        server.url("/").toString().trimEnd('/'), "k", "m", LlmProtocol.OPENAI_RESPONSES
    )

    @Test
    fun `标准路径 - 遍历 output 数组取 output_text，跳过 reasoning 项`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "status": "completed",
                  "output": [
                    {"type": "reasoning"},
                    {"type": "message", "content": [{"type": "output_text", "text": "{\"intent\":\"todo\"}"}]}
                  ],
                  "usage": {"total_tokens": 100}
                }"""
            )
        )
        val r = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        assertEquals("{\"intent\":\"todo\"}", r.text)
        assertEquals(StopReason.COMPLETE, r.stopReason)
        assertEquals(100, r.usageTokens)

        val req = server.takeRequest()
        assertEquals("/v1/responses", req.path)
        assertEquals("Bearer k", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"instructions\""))
        assertTrue(body.contains("\"json_schema\""))
    }

    @Test
    fun `DeepSeek Responses 请求关闭默认思考模式`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status":"completed","output_text":"{}"}""")
        )
        adapter.complete(
            endpoint().copy(model = "deepseek-v4-flash"),
            LlmRequest("s", "u", "intent")
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"reasoning\":{\"effort\":\"none\"}"))
    }

    @Test
    fun `便捷字段 output_text 直返时优先使用`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status": "completed", "output_text": "{\"a\":1}"}"""
            )
        )
        val r = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        assertEquals("{\"a\":1}", r.text)
    }

    @Test
    fun `incomplete + max_output_tokens 识别为截断`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status": "incomplete", "incomplete_details": {"reason": "max_output_tokens"},
                   "output": [{"type":"message","content":[{"type":"output_text","text":"{\"a\":"}]}]}"""
            )
        )
        val r = adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        assertEquals(StopReason.MAX_TOKENS, r.stopReason)
    }

    @Test
    fun `400 报 json_schema 不支持时降级 json_object 重试`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"Invalid json schema: one of type, ref"}}""")
        )
        server.enqueue(
            MockResponse().setBody("""{"status":"completed","output_text":"{}"}""")
        )
        adapter.complete(endpoint(), LlmRequest("s", "u", "intent"))
        server.takeRequest()
        val retried = server.takeRequest()
        val body = retried.body.readUtf8()
        assertTrue(body.contains("\"json_object\""))
        assertFalse(body.contains("\"json_schema\""))
    }
}
