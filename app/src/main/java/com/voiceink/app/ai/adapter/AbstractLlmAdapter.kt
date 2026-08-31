package com.voiceink.app.ai.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 统一错误模型：retriable=true 时由 LlmGateway 指数退避重试 */
class LlmException(
    val httpCode: Int,
    message: String,
    val retriable: Boolean
) : Exception(message)

/** 公共底座（§7.2）：OkHttp 单例、POST、错误映射 */
abstract class AbstractLlmAdapter(
    protected val client: OkHttpClient,
    protected val json: Json
) : LlmAdapter {

    protected suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: JsonObject
    ): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LlmException(resp.code, parseErrorMessage(text), retriable(resp.code))
            }
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse {
                    throw LlmException(-1, "响应不是合法 JSON: ${text.take(200)}", retriable = true)
                }
        }
    }

    protected fun retriable(code: Int) = code == 429 || code >= 500

    protected open fun parseErrorMessage(body: String): String = body.take(300)
}
