package com.voiceink.app.ai

import com.voiceink.app.ai.adapter.LlmAdapterFactory
import com.voiceink.app.ai.adapter.LlmException
import com.voiceink.app.data.repo.SettingsRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

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
            }
        }
        throw lastError ?: error("unreachable")
    }
}
