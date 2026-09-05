package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

    /** SSE 帧有大小限制，收到协议终止事件即关闭连接；取消任务会同时取消 OkHttp。 */
    protected suspend fun postSse(
        url: String,
        headers: Map<String, String>,
        body: JsonObject,
        onConnected: suspend () -> Unit,
        onJson: suspend (JsonObject) -> Unit,
        onEvent: suspend (event: String?, data: String) -> Boolean
    ) = withContext(Dispatchers.IO) {
        coroutineScope {
            val req = Request.Builder().url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = client.newCall(req)
            // 心跳延长的是空闲读取窗口，总时长仍有上限，仅影响整理流请求。
            call.timeout().timeout(240, java.util.concurrent.TimeUnit.SECONDS)
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                currentCoroutineContext().ensureActive()
                call.execute().use { resp ->
                    val responseBody = resp.body
                        ?: throw LlmException(-1, "流式响应为空", true)
                    val source = responseBody.source()
                    if (!resp.isSuccessful) {
                        source.request(4096)
                        throw LlmException(
                            resp.code, parseErrorMessage(source.readUtf8(minOf(source.buffer.size, 4096))),
                            retriable(resp.code)
                        )
                    }
                    onConnected()
                    val subtype = responseBody.contentType()?.subtype.orEmpty()
                    if (subtype == "json" || subtype.endsWith("+json")) {
                        if (source.request(MAX_STREAM_TEXT.toLong() + 1)) {
                            throw LlmException(-1, "响应超过大小限制", false)
                        }
                        onJson(streamJson(source.readUtf8()))
                        return@use
                    }
                    var eventName: String? = null
                    val data = StringBuilder()
                    suspend fun dispatch(): Boolean {
                        val done = data.isNotEmpty() && onEvent(eventName, data.toString())
                        eventName = null
                        data.clear()
                        return done
                    }
                    while (!source.exhausted()) {
                        currentCoroutineContext().ensureActive()
                        val line = source.readUtf8LineStrict(MAX_STREAM_TEXT.toLong())
                        when {
                            line.isEmpty() -> if (dispatch()) return@use
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") -> eventName = line.substring(6).removePrefix(" ")
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substring(5).removePrefix(" "))
                                if (data.length > MAX_STREAM_TEXT) {
                                    throw LlmException(-1, "流式事件超过大小限制", false)
                                }
                            }
                        }
                    }
                    if (!dispatch()) throw LlmException(-1, "流式响应提前中断，请重试", true)
                }
            } catch (error: java.io.IOException) {
                currentCoroutineContext().ensureActive()
                throw error
            } finally {
                cancellation.cancel()
                call.cancel()
            }
        }
    }

    protected fun streamJson(data: String): JsonObject =
        runCatching { json.parseToJsonElement(data).jsonObject }.getOrElse {
            // 不把原始正文或思考内容复制进异常和后台任务数据。
            throw LlmException(-1, "流式响应不是合法 JSON", true)
        }

    protected fun appendStreamText(target: StringBuilder, text: String) {
        if (target.length + text.length > MAX_STREAM_TEXT) {
            throw LlmException(-1, "模型输出超过大小限制", false)
        }
        target.append(text)
    }

    protected fun streamError(error: JsonObject?): Nothing {
        val type = (error?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val retryable = type in setOf("overloaded_error", "rate_limit_error", "server_error", "api_error")
        throw LlmException(-1, "模型流式响应失败", retryable)
    }

    private companion object {
        const val MAX_STREAM_TEXT = 1024 * 1024
    }

    protected fun retriable(code: Int) = code == 429 || code >= 500

    /** 拼接 API 地址：用户填的 Base URL 可能自带 /v1（如硅基流动），避免拼成 /v1/v1/… */
    protected fun apiUrl(baseUrl: String, path: String): String {
        val b = baseUrl.trimEnd('/')
        return if (b.endsWith("/v1")) b + path else b + "/v1" + path
    }

    /** DeepSeek V4 默认开启思考；结构化抽取需把输出额度留给 JSON 正文。 */
    protected fun isDeepSeekEndpoint(endpoint: LlmEndpoint): Boolean {
        val model = endpoint.model.trim().lowercase()
        val baseUrl = endpoint.baseUrl.lowercase()
        return model.startsWith("deepseek-") || baseUrl.contains("api.deepseek.com")
    }

    protected open fun parseErrorMessage(body: String): String = body.take(300)
}
