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
                val msg = e.message.orEmpty()
                if (e.httpCode == 400 && useJsonSchema &&
                    (msg.contains("json_schema") || msg.contains("text.format") || msg.contains("format"))
                ) {
                    useJsonSchema = false
                } else {
                    throw e
                }
            }
        }
        error("unreachable")
    }

    private fun buildBody(endpoint: LlmEndpoint, request: LlmRequest, useJsonSchema: Boolean): JsonObject =
        buildJsonObject {
            put("model", endpoint.model)
            put("instructions", request.system)
            putJsonArray("input") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "input_text"); put("text", request.user) })
                    }
                })
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
            put("temperature", request.temperature)
            put("max_output_tokens", request.maxTokens)
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
            ?: throw LlmException(
                -1, "Responses 输出中未找到 output_text: ${resp.toString().take(300)}", false
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
        resp["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull
}
