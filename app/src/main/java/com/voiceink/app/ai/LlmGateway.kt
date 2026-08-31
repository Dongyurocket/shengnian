package com.voiceink.app.ai

import com.voiceink.app.ai.adapter.LlmAdapterFactory
import com.voiceink.app.ai.adapter.LlmException
import com.voiceink.app.data.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal fun connectionTestMessage(responseText: String): String =
    if (responseText.isBlank()) "连接成功（模型未返回内容）" else "连接成功"

/**
 * 统一出口（§7.2）：读配置 → 选 adapter → 仅对可重试错误指数退避（1s/2s，最多 3 次）。
 * 401/403 等致命错误直接抛给上层提示用户检查 Key。
 */
@Singleton
class LlmGateway @Inject constructor(
    private val factory: LlmAdapterFactory,
    private val settings: SettingsRepository
) {
    suspend fun complete(request: LlmRequest): LlmResult {
        val endpoint = settings.currentEndpoint()
        require(endpoint.baseUrl.isNotBlank()) { "未配置 LLM Base URL，请到设置页填写" }
        val adapter = factory.create(endpoint.protocol)
        var delayMs = 1000L
        var lastError: LlmException? = null
        repeat(3) { attempt ->
            try {
                return adapter.complete(endpoint, request)
            } catch (e: LlmException) {
                if (!e.retriable) throw e
                lastError = e
                if (attempt < 2) {
                    delay(delayMs)
                    delayMs *= 2
                }
            } catch (e: IOException) {
                // OkHttp 的阻断/超时也应交给 WorkManager 前的网关短暂重试。
                currentCoroutineContext().ensureActive()
                lastError = LlmException(
                    httpCode = -1,
                    message = "网络请求失败：${e.message?.take(120).orEmpty()}",
                    retriable = true
                )
                if (attempt < 2) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        throw lastError ?: error("unreachable")
    }

    /** 设置页「测试连接」（§6.3）：用表单中的端点（可能未保存）发一次最小请求 */
    suspend fun testEndpoint(endpoint: LlmEndpoint): String {
        if (endpoint.baseUrl.isBlank()) return "请填写 Base URL"
        if (endpoint.model.isBlank()) return "请填写模型名"
        return try {
            val adapter = factory.create(endpoint.protocol)
            val r = adapter.complete(
                endpoint,
                LlmRequest(
                    system = "你只输出一个合法 JSON 对象（json）。",
                    user = "请输出一个 JSON 对象，示例：{\"ok\":true}",
                    jsonSchemaName = "intent",
                    // 推理模型会先消耗思考 token，512 比 64 更不容易造成空响应误判
                    maxTokens = 512
                )
            )
            // 连接测试只判断端点和协议响应是否成功；业务 JSON 由流水线统一兜底解析。
            connectionTestMessage(r.text)
        } catch (e: LlmException) {
            "失败（HTTP ${e.httpCode}）：${e.message?.take(80)}"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            "失败：${e.message?.take(80)}"
        }
    }
}
