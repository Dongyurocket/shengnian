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
        val url = endpoint.baseUrl.trimEnd('/') + "/v1/messages"
        val body = buildJsonObject {
            put("model", endpoint.model)
            put("max_tokens", request.maxTokens)   // 必填
            put("system", request.system)          // 顶层 system，不进 messages
            put("temperature", request.temperature)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", request.user) })
                    }
                })
                // JSON 预填：强制模型从 '{' 后续写
                add(buildJsonObject {
                    put("role", "assistant")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", "{") })
                    }
                })
            }
        }
        val resp = post(
            url, mapOf(
                "x-api-key" to endpoint.apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            ), body
        )
        return parse(resp)
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
        return LlmResult(
            text = "{" + partial.trimStart(),   // 拼回 prefill 的 '{'
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
