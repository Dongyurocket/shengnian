package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.LlmResult
import com.voiceink.app.ai.StopReason
import com.voiceink.app.ai.prompt.Prompts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * OpenAI Responses API（§7.4）。
 * 差异点：instructions 承载 system；input 为结构化数组；响应取 output[*] 中
 * type=="message" 项的 output_text；结构化输出用 text.format=json_schema(strict)，
 * 中转不支持时 400 降级为 json_object 重试。
 */
class OpenAiResponsesAdapter @Inject constructor(
    client: OkHttpClient,
    json: Json
) : AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.OPENAI_RESPONSES

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val url = apiUrl(endpoint.baseUrl, "/responses")
        val headers = mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        )
        var useJsonSchema = true
        repeat(2) {
            val body = buildBody(endpoint, request, useJsonSchema)
            try {
                return parse(post(url, headers, body))
            } catch (e: LlmException) {
                val msg = e.message.orEmpty().lowercase()
                val formatError = msg.contains("json_schema") ||
                    msg.contains("json schema") ||
                    msg.contains("text.format") ||
                    msg.contains("response_format") ||
                    msg.contains("schema")
                // 仅对结构化输出格式错误降级，模型名等普通 400 不重复消耗请求
                if (e.httpCode == 400 && useJsonSchema && formatError) {
                    useJsonSchema = false
                } else {
                    throw e
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
        val url = apiUrl(endpoint.baseUrl, "/responses")
        val headers = mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        )
        var useJsonSchema = true
        var streamEndpoint = endpoint
        repeat(3) {
            val text = StringBuilder()
            var completed: JsonObject? = null
            var jsonResult: LlmResult? = null
            var summaryLength = 0
            try {
                postSse(
                    url, headers, buildBody(streamEndpoint, request, useJsonSchema, stream = true),
                    onConnected = { onEvent(LlmStreamEvent.Connected) },
                    onJson = { jsonResult = parse(it) }
                ) { event, data ->
                    val chunk = streamJson(data)
                    (chunk["error"] as? JsonObject)?.let { streamError(it) }
                    val type = chunk["type"]?.jsonPrimitive?.contentOrNull ?: event
                    when (type) {
                        "response.output_text.delta" -> {
                            val delta = chunk["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (delta.isNotEmpty()) {
                                appendStreamText(text, delta)
                                onEvent(LlmStreamEvent.TextDelta(delta))
                            }
                        }
                        "response.reasoning_summary_text.delta" -> {
                            if (streamEndpoint.showReasoningSummary && streamEndpoint.thinkingEnabled) {
                                val delta = chunk["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    .take((600 - summaryLength).coerceAtLeast(0))
                                summaryLength += delta.length
                                if (delta.isNotEmpty()) onEvent(LlmStreamEvent.ReasoningSummaryDelta(delta))
                            }
                        }
                        "response.completed", "response.incomplete" -> {
                            completed = chunk["response"]?.jsonObject
                                ?: throw LlmException(-1, "流式响应缺少完成结果", true)
                            return@postSse true
                        }
                        "response.failed", "error" -> streamError(
                            chunk["response"]?.jsonObject?.get("error") as? JsonObject
                        )
                    }
                    false
                }
                jsonResult?.let { return it }
                val response = completed ?: throw LlmException(-1, "流式响应提前中断", true)
                if (response.containsKey("output") || response.containsKey("output_text")) return parse(response)
                return LlmResult(
                    text.toString(),
                    stopReason(response["status"]?.jsonPrimitive?.contentOrNull, response),
                    usage(response)
                )
            } catch (e: LlmException) {
                val msg = e.message.orEmpty().lowercase()
                val formatError = msg.contains("json_schema") || msg.contains("json schema") ||
                    msg.contains("text.format") || msg.contains("response_format") || msg.contains("schema")
                when {
                    e.httpCode == 400 && streamEndpoint.showReasoningSummary && msg.contains("summary") ->
                        streamEndpoint = streamEndpoint.copy(showReasoningSummary = false)
                    e.httpCode == 400 && useJsonSchema && formatError -> useJsonSchema = false
                    else -> throw e
                }
            }
        }
        error("unreachable")
    }

    private fun buildBody(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        useJsonSchema: Boolean,
        stream: Boolean = false
    ): JsonObject =
        buildJsonObject {
            put("model", endpoint.model)
            if (stream) put("stream", true)
            put("instructions", request.system)
            putJsonArray("input") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", request.user)
                        })
                        request.images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "input_image")
                                put("image_url", image.dataUrl())
                                put("detail", "auto")
                            })
                        }
                    }
                })
            }
            if (isDeepSeekEndpoint(endpoint)) {
                putJsonObject("reasoning") {
                    put("effort", if (endpoint.thinkingEnabled) endpoint.thinkingEffort.wire else "none")
                }
            } else if (endpoint.thinkingEnabled) {
                putJsonObject("reasoning") {
                    put("effort", endpoint.thinkingEffort.wire)
                    if (stream && endpoint.showReasoningSummary) put("summary", "auto")
                }
            }
            putJsonObject("text") {
                putJsonObject("format") {
                    if (useJsonSchema) {
                        put("type", "json_schema")
                        put("name", request.jsonSchemaName)
                        put("strict", true)
                        put("schema", Prompts.schemaFor(request.jsonSchemaName))
                    } else {
                        put("type", "json_object")
                    }
                }
            }
            if (!endpoint.thinkingEnabled) put("temperature", request.temperature)
            val outputTokens = request.maxTokens +
                if (endpoint.thinkingEnabled) endpoint.thinkingEffort.budgetTokens else 0
            put("max_output_tokens", outputTokens)
        }

    private fun parse(resp: JsonObject): LlmResult {
        val status = resp["status"]?.jsonPrimitive?.contentOrNull
        // 便捷字段 output_text 存在时直接用
        resp["output_text"]?.jsonPrimitive?.contentOrNull?.let {
            return LlmResult(it, stopReason(status, resp), usage(resp))
        }
        // 标准路径：遍历 output 数组（跳过 reasoning / tool_call 项）
        val text = resp["output"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "message" }
            ?.flatMap { it.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList()) }
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.takeIf { it.isNotBlank() }
            ?: if (stopReason(status, resp) == StopReason.MAX_TOKENS) "" else throw LlmException(
                -1, "Responses 输出中未找到 output_text", false
            )
        return LlmResult(text, stopReason(status, resp), usage(resp))
    }

    private fun stopReason(status: String?, resp: JsonObject) = when {
        status == "incomplete" &&
            resp["incomplete_details"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull ==
            "max_output_tokens" -> StopReason.MAX_TOKENS
        status == "completed" -> StopReason.COMPLETE
        else -> StopReason.OTHER
    }

    private fun usage(resp: JsonObject) =
        (resp["usage"] as? JsonObject)?.get("total_tokens")?.jsonPrimitive?.intOrNull
}
