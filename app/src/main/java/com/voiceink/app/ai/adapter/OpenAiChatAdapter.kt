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
 * OpenAI Chat Completions（§7.3）。
 * 兼容策略：400 且报 max_completion_tokens → 换字段重试一次；
 * 400 且报 response_format 不支持 → 去掉该字段重试（最终由 JsonExtractor 兜底）。
 */
class OpenAiChatAdapter @Inject constructor(
    client: OkHttpClient,
    json: Json
) : AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.OPENAI_CHAT

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val url = apiUrl(endpoint.baseUrl, "/chat/completions")
        val headers = mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        )

        var useCompletionTokens = false
        var useResponseFormat = true

        repeat(3) { attempt ->
            val body = buildBody(endpoint, request, useCompletionTokens, useResponseFormat)
            try {
                return parse(post(url, headers, body))
            } catch (e: LlmException) {
                val msg = e.message.orEmpty()
                when {
                    e.httpCode == 400 && !useCompletionTokens &&
                        msg.contains("max_completion_tokens") ->
                        useCompletionTokens = true

                    e.httpCode == 400 && useResponseFormat &&
                        (msg.contains("response_format") || msg.contains("json_object")) ->
                        useResponseFormat = false

                    else -> throw e
                }
            }
        }
        error("unreachable")
    }

    override suspend fun completeStreaming(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        onEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResult {
        val url = apiUrl(endpoint.baseUrl, "/chat/completions")
        val headers = mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        )
        var useCompletionTokens = false
        var useResponseFormat = true

        repeat(3) {
            val text = StringBuilder()
            var finish: String? = null
            var usageTokens: Int? = null
            var jsonResult: LlmResult? = null
            try {
                postSse(
                    url,
                    headers,
                    buildBody(endpoint, request, useCompletionTokens, useResponseFormat, stream = true),
                    onConnected = { onEvent(LlmStreamEvent.Connected) },
                    onJson = { jsonResult = parse(it) }
                ) { _, data ->
                    if (data == "[DONE]") return@postSse true
                    val chunk = streamJson(data)
                    (chunk["error"] as? JsonObject)?.let { streamError(it) }
                    val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    val delta = choice?.get("delta") as? JsonObject
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (content.isNotEmpty()) {
                        appendStreamText(text, content)
                        onEvent(LlmStreamEvent.TextDelta(content))
                    }
                    finish = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull ?: finish
                    usageTokens = (chunk["usage"] as? JsonObject)?.get("total_tokens")
                        ?.jsonPrimitive?.intOrNull ?: usageTokens
                    false
                }
                jsonResult?.let { return it }
                if (finish == null) throw LlmException(-1, "流式响应缺少完成原因", true)
                if (text.isEmpty() && finish != "length") {
                    throw LlmException(-1, "流式响应未包含正文", retriable = true)
                }
                return LlmResult(
                    text = text.toString(),
                    stopReason = when (finish) {
                        "length" -> StopReason.MAX_TOKENS
                        "stop" -> StopReason.COMPLETE
                        else -> StopReason.OTHER
                    },
                    usageTokens = usageTokens
                )
            } catch (e: LlmException) {
                val msg = e.message.orEmpty()
                when {
                    e.httpCode == 400 && !useCompletionTokens &&
                        msg.contains("max_completion_tokens") -> useCompletionTokens = true
                    e.httpCode == 400 && useResponseFormat &&
                        (msg.contains("response_format") || msg.contains("json_object")) -> useResponseFormat = false
                    else -> throw e
                }
            }
        }
        error("unreachable")
    }

    private fun buildBody(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        useCompletionTokens: Boolean,
        useResponseFormat: Boolean,
        stream: Boolean = false
    ): JsonObject = buildJsonObject {
        put("model", endpoint.model)
        if (stream) put("stream", true)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                put("content", request.system)
            })
            add(buildJsonObject {
                put("role", "user")
                if (request.images.isEmpty()) {
                    // 保持纯文本请求的兼容 payload。
                    put("content", request.user)
                } else {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", request.user)
                        })
                        request.images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", image.dataUrl())
                                    put("detail", "auto")
                                }
                            })
                        }
                    }
                }
            })
        }
        if (useResponseFormat) {
            putJsonObject("response_format") { put("type", "json_object") }
        }
        if (!endpoint.thinkingEnabled) put("temperature", request.temperature)
        if (isDeepSeekEndpoint(endpoint)) {
            // DeepSeek 兼容接口用 thinking.type 控制开关。
            putJsonObject("thinking") {
                put("type", if (endpoint.thinkingEnabled) "enabled" else "disabled")
            }
        } else if (endpoint.thinkingEnabled) {
            // OpenAI 兼容 reasoning 模型使用 reasoning_effort。
            put("reasoning_effort", endpoint.thinkingEffort.wire)
        }
        val outputTokens = request.maxTokens +
            if (endpoint.thinkingEnabled) endpoint.thinkingEffort.budgetTokens else 0
        if (useCompletionTokens) put("max_completion_tokens", outputTokens)
        else put("max_tokens", outputTokens)
    }

    private fun parse(resp: JsonObject): LlmResult {
        val choice = resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmException(-1, "响应缺少 choices: ${resp.toString().take(300)}", false)
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw LlmException(-1, "choices[0].message.content 缺失", false)
        val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        return LlmResult(
            text = content,
            stopReason = if (finish == "length") StopReason.MAX_TOKENS else StopReason.COMPLETE,
            usageTokens = resp["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull
        )
    }

    override fun parseErrorMessage(body: String): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull() ?: super.parseErrorMessage(body)
}
