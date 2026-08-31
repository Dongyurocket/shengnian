package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmImage
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultimodalAdapterTest {
    private val server = MockWebServer()
    private val image = LlmImage("image/png", "aGVsbG8=")
    private val request = LlmRequest("system", "请识别图片", "intent", images = listOf(image))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `Chat 使用 image_url data URL`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
        OpenAiChatAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })
            .complete(endpoint(LlmProtocol.OPENAI_CHAT), request)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"type\":\"image_url\""))
        assertTrue(body.contains("data:image/png;base64,aGVsbG8="))
    }

    @Test
    fun `Responses 使用 input_image`() = runTest {
        server.enqueue(MockResponse().setBody("""{"output_text":"{}","status":"completed"}"""))
        OpenAiResponsesAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })
            .complete(endpoint(LlmProtocol.OPENAI_RESPONSES), request)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"type\":\"input_image\""))
        assertTrue(body.contains("data:image/png;base64,aGVsbG8="))
    }

    @Test
    fun `Anthropic 使用 base64 image source 并保留预填`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"\"intent\":\"note\"}"}],"stop_reason":"end_turn"}"""))
        AnthropicMessagesAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })
            .complete(endpoint(LlmProtocol.ANTHROPIC_MESSAGES), request)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"type\":\"image\""))
        assertTrue(body.contains("\"type\":\"base64\""))
        assertTrue(body.contains("\"role\":\"assistant\""))
    }

    private fun endpoint(protocol: LlmProtocol) = LlmEndpoint(
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "k",
        model = "vision-model",
        protocol = protocol
    )
}
