package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.LlmResult
import com.voiceink.app.ai.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Anthropic Messages API（§7.5）。
 * 差异点：x-api-key + anthropic-version 头；system 为顶层字段；max_tokens 必填；
 * 无 response_format —— 用 assistant 预填 "{" 强制 JSON，解析时拼回。
 * （提前于阶段 6 写入：适配器工厂需要完整分支；契约测试在阶段 6 补齐）
 */
class AnthropicMessagesAdapter @Inject constructor(
    client: OkHttpClient,
    json: Json
) : AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.ANTHROPIC_MESSAGES

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val resp = post(apiUrl(endpoint.baseUrl, "/messages"), headers(endpoint), buildBody(endpoint, request))
        return parse(resp)
    }

    override suspend fun completeStreaming(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        onEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResult {
        val text = StringBuilder()
        var stop: String? = null
        var inputTokens = 0
        var outputTokens = 0
        var jsonResult: LlmResult? = null
        postSse(
            apiUrl(endpoint.baseUrl, "/messages"), headers(endpoint), buildBody(endpoint, request, stream = true),
            onConnected = { onEvent(LlmStreamEvent.Connected) },
            onJson = { jsonResult = parse(it) }
        ) { event, data ->
            val chunk = streamJson(data)
            val type = chunk["type"]?.jsonPrimitive?.contentOrNull ?: event
            when (type) {
                "message_start" -> {
                    val usage = chunk["message"]?.jsonObject?.get("usage")?.jsonObject
                    inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
                }
                "content_block_start" -> {
                    val block = chunk["content_block"]?.jsonObject
                    if (block?.get("type")?.jsonPrimitive?.contentOrNull == "text") {
                        val content = block["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (content.isNotEmpty()) {
                            appendStreamText(text, content)
                            onEvent(LlmStreamEvent.TextDelta(content))
                        }
                    }
                }
                "content_block_delta" -> {
                    val delta = chunk["delta"]?.jsonObject
                    if (delta?.get("type")?.jsonPrimitive?.contentOrNull == "text_delta") {
                        val content = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (content.isNotEmpty()) {
                            appendStreamText(text, content)
                            onEvent(LlmStreamEvent.TextDelta(content))
                        }
                    }
                    // thinking_delta 是原始思考内容，不作为推理摘要展示或持久化。
                }
                "message_delta" -> {
                    stop = chunk["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull ?: stop
                    outputTokens = chunk["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: outputTokens
                }
                "message_stop" -> return@postSse true
                "error" -> streamError(chunk["error"] as? JsonObject)
            }
            false
        }
        jsonResult?.let { return it }
        if (stop == null) throw LlmException(-1, "流式响应缺少完成原因", true)
        val continuation = text.toString().trimStart()
        if (continuation.isBlank() && stop != "max_tokens") throw LlmException(-1, "流式响应未包含正文", true)
        return LlmResult(
            text = if (!endpoint.thinkingEnabled && !continuation.startsWith("{")) "{$continuation" else continuation,
            stopReason = when (stop) {
                "max_tokens" -> StopReason.MAX_TOKENS
                "end_turn", "stop_sequence" -> StopReason.COMPLETE
                else -> StopReason.OTHER
            },
            usageTokens = inputTokens + outputTokens
        )
    }

    private fun headers(endpoint: LlmEndpoint) = mapOf(
        "x-api-key" to endpoint.apiKey,
        "anthropic-version" to "2023-06-01",
        "Content-Type" to "application/json"
    )

    private fun buildBody(endpoint: LlmEndpoint, request: LlmRequest, stream: Boolean = false) = buildJsonObject {
            put("model", endpoint.model)
            if (stream) put("stream", true)
            val thinkingBudget = if (endpoint.thinkingEnabled) endpoint.thinkingEffort.budgetTokens else 0
            put("max_tokens", request.maxTokens + thinkingBudget)   // Anthropic 的 thinking budget 计入上限
            put("system", request.system)          // 顶层 system，不进 messages
            if (!endpoint.thinkingEnabled) put("temperature", request.temperature)
            if (endpoint.thinkingEnabled) {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", thinkingBudget)
                }
            } else if (isDeepSeekEndpoint(endpoint)) {
                putJsonObject("thinking") { put("type", "disabled") }
            }
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", request.user)
                        })
                        request.images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", image.mimeType)
                                    put("data", image.base64)
                                }
                            })
                        }
                    }
                })
                if (!endpoint.thinkingEnabled) {
                    // 关闭思考时用 JSON 预填；Anthropic 思考模式不允许预填 assistant。
                    add(buildJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            add(buildJsonObject { put("type", "text"); put("text", "{") })
                        }
                    })
                }
            }
        }

    private fun parse(resp: JsonObject): LlmResult {
        val partial = resp["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?: throw LlmException(-1, "Anthropic 响应缺少 content: ${resp.toString().take(300)}", false)
        val stop = resp["stop_reason"]?.jsonPrimitive?.contentOrNull
        val usage = resp["usage"]?.jsonObject?.let {
            (it["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0) +
                (it["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0)
        }
        val continuation = partial.trimStart()
        val text = when {
            continuation.isBlank() -> ""
            continuation.startsWith("{") -> continuation
            else -> "{" + continuation
        }
        return LlmResult(
            text = text,                         // 拼回 prefill 的 '{'，避免重复拼接
            stopReason = if (stop == "max_tokens") StopReason.MAX_TOKENS else StopReason.COMPLETE,
            usageTokens = usage
        )
    }

    override fun parseErrorMessage(body: String): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull() ?: super.parseErrorMessage(body)
}
