package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamingAdapterTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val request = LlmRequest("system", "user", "intent")
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun endpoint(protocol: LlmProtocol, summary: Boolean = false, thinking: Boolean = false) =
        LlmEndpoint(server.url("/").toString(), "key", "model", protocol, thinking,
            showReasoningSummary = summary)

    private fun adapter(protocol: LlmProtocol, http: OkHttpClient = client): LlmAdapter = when (protocol) {
        LlmProtocol.OPENAI_CHAT -> OpenAiChatAdapter(http, json)
        LlmProtocol.OPENAI_RESPONSES -> OpenAiResponsesAdapter(http, json)
        LlmProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesAdapter(http, json)
    }

    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
    // QueueDispatcher.peek also throttles request uploads; these tests isolate response reads.
    private fun respondAfterRequest(response: MockResponse) {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = response
        }
    }
    private fun frame(data: String, event: String? = null) =
        (event?.let { "event: $it\n" } ?: "") + "data: $data\n\n"
    private fun chatDelta(text: String) = frame(buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject {
            putJsonObject("delta") { put("content", text) }
        }) }
    }.toString())
    private fun chatEnd(reason: String = "stop") =
        frame("""{"choices":[{"delta":{},"finish_reason":"$reason"}]}""") + frame("[DONE]")
    private fun responsesEnd(status: String = "completed") = frame(
        """{"type":"response.$status","response":{"status":"$status"}}"""
    )
    private fun anthropicEnd(reason: String = "end_turn") =
        frame("""{"type":"message_delta","delta":{"stop_reason":"$reason"},"usage":{"output_tokens":3}}""") +
            frame("""{"type":"message_stop"}""")

    @Test fun `chat aggregates text and ignores raw reasoning`() = runTest {
        server.enqueue(sse(": heartbeat\n\n" +
            frame("""{"choices":[{"delta":{"reasoning_content":"PRIVATE"}}]}""") +
            chatDelta("{\"ok\":") + chatDelta("true}") + chatEnd()))
        val events = mutableListOf<LlmStreamEvent>()
        val result = adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(
            endpoint(LlmProtocol.OPENAI_CHAT, summary = true, thinking = true), request, events::add
        )
        assertEquals("{\"ok\":true}", result.text)
        assertEquals(StopReason.COMPLETE, result.stopReason)
        assertEquals(LlmStreamEvent.Connected, events.first())
        assertEquals(2, events.filterIsInstance<LlmStreamEvent.TextDelta>().size)
        assertFalse(events.toString().contains("PRIVATE"))
        val sent = server.takeRequest()
        assertEquals("text/event-stream", sent.getHeader("Accept"))
        assertTrue(json.parseToJsonElement(sent.body.readUtf8()).jsonObject["stream"]!!.jsonPrimitive.boolean)
    }

    @Test fun `responses summary is opt in bounded and never includes reasoning deltas`() = runTest {
        for (enabled in listOf(false, true)) {
            val summary = "s".repeat(900)
            server.enqueue(sse(
                frame("""{"type":"response.reasoning_text.delta","delta":"PRIVATE"}""") +
                frame("""{"type":"response.reasoning_summary_text.delta","delta":"$summary"}""") +
                frame("""{"type":"response.output_text.delta","delta":"{}"}""") + responsesEnd()
            ))
            val events = mutableListOf<LlmStreamEvent>()
            val result = adapter(LlmProtocol.OPENAI_RESPONSES).completeStreaming(
                endpoint(LlmProtocol.OPENAI_RESPONSES, enabled, thinking = true), request, events::add
            )
            assertEquals("{}", result.text)
            val shown = events.filterIsInstance<LlmStreamEvent.ReasoningSummaryDelta>().joinToString("") { it.text }
            assertEquals(if (enabled) "s".repeat(600) else "", shown)
            assertFalse(events.toString().contains("PRIVATE"))
            val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals(enabled, body["reasoning"]!!.jsonObject.containsKey("summary"))
        }
    }

    @Test fun `anthropic preserves prefill and ignores thinking`() = runTest {
        server.enqueue(sse(
            frame("""{"type":"message_start","message":{"usage":{"input_tokens":5}}}""") +
            frame("""{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"PRIVATE"}}""") +
            frame("""{"type":"content_block_start","content_block":{"type":"text","text":""}}""") +
            frame("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"\"ok\":true}"}}""") + anthropicEnd()
        ))
        val events = mutableListOf<LlmStreamEvent>()
        val result = adapter(LlmProtocol.ANTHROPIC_MESSAGES).completeStreaming(
            endpoint(LlmProtocol.ANTHROPIC_MESSAGES, summary = true), request, events::add
        )
        assertEquals("{\"ok\":true}", result.text)
        assertEquals(8, result.usageTokens)
        assertFalse(events.toString().contains("PRIVATE"))
    }

    @Test fun `every protocol rejects even valid JSON without terminal event`() = runTest {
        val streams = mapOf(
            LlmProtocol.OPENAI_CHAT to chatDelta("{}"),
            LlmProtocol.OPENAI_RESPONSES to frame("""{"type":"response.output_text.delta","delta":"{}"}"""),
            LlmProtocol.ANTHROPIC_MESSAGES to frame("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"{}"}}""")
        )
        for ((protocol, body) in streams) {
            server.enqueue(sse(body))
            val error = runCatching { adapter(protocol).completeStreaming(endpoint(protocol), request) {} }.exceptionOrNull()
            assertTrue("$protocol: $error", error is LlmException && error.retriable)
        }
    }

    @Test fun `all protocols propagate max tokens without committing success`() = runTest {
        val streams = mapOf(
            LlmProtocol.OPENAI_CHAT to (chatDelta("{") + chatEnd("length")),
            LlmProtocol.OPENAI_RESPONSES to frame("""{"type":"response.incomplete","response":{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}"""),
            LlmProtocol.ANTHROPIC_MESSAGES to anthropicEnd("max_tokens")
        )
        for ((protocol, body) in streams) {
            server.enqueue(sse(body))
            assertEquals(StopReason.MAX_TOKENS, adapter(protocol).completeStreaming(endpoint(protocol), request) {}.stopReason)
        }
    }

    @Test fun `error inside stream is not a successful partial result`() = runTest {
        for (protocol in LlmProtocol.entries) {
            server.enqueue(sse(frame("""{"type":"error","error":{"type":"overloaded_error","message":"PRIVATE"}}""")))
            val error = runCatching { adapter(protocol).completeStreaming(endpoint(protocol), request) {} }.exceptionOrNull()
            assertTrue(error is LlmException && error.retriable)
            assertFalse(error?.message.orEmpty().contains("PRIVATE"))
        }
    }

    @Test fun `explicit JSON response remains compatible for every protocol`() = runTest {
        val responses = mapOf(
            LlmProtocol.OPENAI_CHAT to """{"choices":[{"message":{"content":"{}"},"finish_reason":"stop"}]}""",
            LlmProtocol.OPENAI_RESPONSES to """{"output_text":"{}","status":"completed"}""",
            LlmProtocol.ANTHROPIC_MESSAGES to """{"content":[{"type":"text","text":"{}"}],"stop_reason":"end_turn"}"""
        )
        for ((protocol, body) in responses) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
            assertEquals("{}", adapter(protocol).completeStreaming(endpoint(protocol), request) {}.text)
        }
    }

    @Test fun `chat still negotiates token and format fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("max_completion_tokens required"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("response_format unsupported"))
        server.enqueue(sse(chatDelta("{}") + chatEnd()))
        assertEquals("{}", adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}.text)
        val bodies = List(3) { json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject }
        assertTrue(bodies.all { it["stream"]!!.jsonPrimitive.boolean })
        assertTrue(bodies[1].containsKey("max_completion_tokens"))
        assertFalse(bodies[2].containsKey("response_format"))
    }

    @Test fun `unsupported summary and schema fall back independently`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("reasoning.summary unsupported"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("json_schema unsupported"))
        server.enqueue(sse(frame("""{"type":"response.output_text.delta","delta":"{}"}""") + responsesEnd()))
        val events = mutableListOf<LlmStreamEvent>()
        adapter(LlmProtocol.OPENAI_RESPONSES).completeStreaming(
            endpoint(LlmProtocol.OPENAI_RESPONSES, summary = true, thinking = true), request, events::add
        )
        val bodies = List(3) { json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject }
        assertTrue(bodies[0]["reasoning"]!!.jsonObject.containsKey("summary"))
        assertFalse(bodies[1]["reasoning"]!!.jsonObject.containsKey("summary"))
        assertEquals("json_object", bodies[2]["text"]!!.jsonObject["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1, events.count { it is LlmStreamEvent.Connected })
    }

    @Test fun `SSE supports CRLF multiline data comments and unknown events`() = runTest {
        server.enqueue(sse(
            "event: ignored\r\ndata: {\r\ndata: \"choices\":[{\"delta\":{\"content\":\"{}\"}}]}\r\n\r\n" +
                ": heartbeat\r\n\r\n" + chatEnd()
        ))
        assertEquals("{}", adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}.text)
    }

    @Test fun `terminal event closes a connection that remains open`() = runTest {
        respondAfterRequest(sse(chatDelta("{}") + chatEnd() + ":".repeat(1000)).throttleBody(300, 2, TimeUnit.SECONDS))
        withContext(Dispatchers.Default) {
            withTimeout(1500) {
                assertEquals("{}", adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}.text)
            }
        }
    }

    @Test fun `cancellation promptly closes a blocked body read`() = runBlocking {
        respondAfterRequest(sse(": heartbeat\n\n" + " ".repeat(100)).throttleBody(13, 2, TimeUnit.SECONDS))
        val connected = CompletableDeferred<Unit>()
        val task = launch(Dispatchers.Default) {
            adapter(LlmProtocol.OPENAI_CHAT, client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build())
                .completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {
                    if (it is LlmStreamEvent.Connected) connected.complete(Unit)
                }
        }
        withTimeout(2000) { connected.await() }
        withTimeout(1000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
    }

    @Test fun `cancellation before headers also closes the call`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task = launch(Dispatchers.Default) {
            adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}
        }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        withTimeout(2000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
    }

    @Test fun `heartbeats keep idle timeout alive across a longer response`() = runBlocking {
        val body = ": ping\n\n".repeat(12) + chatDelta("{}") + chatEnd()
        respondAfterRequest(sse(body).throttleBody(8, 50, TimeUnit.MILLISECONDS))
        val shortIdle = client.newBuilder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val started = System.nanoTime()
        assertEquals("{}", adapter(LlmProtocol.OPENAI_CHAT, shortIdle).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}.text)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 300)
    }

    @Test fun `silent stream times out instead of hanging`() = runBlocking {
        respondAfterRequest(sse(": ping\n\n" + " ".repeat(30)).throttleBody(8, 2, TimeUnit.SECONDS))
        val shortIdle = client.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()
        val error = runCatching { adapter(LlmProtocol.OPENAI_CHAT, shortIdle).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {} }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
    }

    @Test fun `chat accepts nullable usage and error fields`() = runTest {
        server.enqueue(sse(
            frame("""{"choices":[{"delta":{"content":"{}"}}],"usage":null,"error":null}""") + chatEnd()
        ))
        assertEquals("{}", adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {}.text)
    }

    @Test fun `responses reasoning only incomplete terminal preserves max token reason`() = runTest {
        server.enqueue(sse(frame("""{"type":"response.incomplete","response":{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"reasoning","summary":[]}],"usage":null}}""")))
        val result = adapter(LlmProtocol.OPENAI_RESPONSES).completeStreaming(endpoint(LlmProtocol.OPENAI_RESPONSES), request) {}
        assertEquals(StopReason.MAX_TOKENS, result.stopReason)
        assertEquals("", result.text)
    }

    @Test fun `responses final envelope is authoritative and completion event needs no connection close`() = runTest {
        server.enqueue(sse(
            frame("""{"type":"response.output_text.delta","delta":"{\"incomplete\":"}""") +
                frame("""{"type":"response.completed","response":{"status":"completed","output":[{"type":"reasoning","summary":[]},{"type":"message","content":[{"type":"output_text","text":"{}"}]}]}}""")
        ))
        assertEquals("{}", adapter(LlmProtocol.OPENAI_RESPONSES).completeStreaming(endpoint(LlmProtocol.OPENAI_RESPONSES), request) {}.text)
    }

    @Test fun `streaming retains multimodal request shape for every protocol`() = runTest {
        val finishes = mapOf(
            LlmProtocol.OPENAI_CHAT to (chatDelta("{}") + chatEnd()),
            LlmProtocol.OPENAI_RESPONSES to (frame("""{"type":"response.output_text.delta","delta":"{}"}""") + responsesEnd()),
            LlmProtocol.ANTHROPIC_MESSAGES to (frame("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"{}"}}""") + anthropicEnd())
        )
        for ((protocol, body) in finishes) {
            server.enqueue(sse(body))
            adapter(protocol).completeStreaming(endpoint(protocol, thinking = true), request.copy(images = listOf(LlmImage("image/png", "aGVsbG8=")))) {}
            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("aGVsbG8="))
            assertTrue(sent.contains("\"stream\":true"))
            if (protocol == LlmProtocol.ANTHROPIC_MESSAGES) assertFalse(sent.contains("\"role\":\"assistant\""))
        }
    }

    @Test fun `stream output size is bounded`() = runTest {
        server.enqueue(sse(chatDelta("x".repeat(600000)) + chatDelta("x".repeat(600000)) + chatEnd()))
        val error = runCatching { adapter(LlmProtocol.OPENAI_CHAT).completeStreaming(endpoint(LlmProtocol.OPENAI_CHAT), request) {} }.exceptionOrNull()
        assertTrue(error is LlmException && !error.retriable)
    }
}
