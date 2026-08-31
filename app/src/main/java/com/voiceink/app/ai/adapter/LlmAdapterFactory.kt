package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmProtocol
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** 适配器工厂：按协议选择实现（Responses / Anthropic 在阶段 6 加入） */
@Singleton
class LlmAdapterFactory @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun create(protocol: LlmProtocol): LlmAdapter = when (protocol) {
        LlmProtocol.OPENAI_CHAT -> OpenAiChatAdapter(client, json)
        LlmProtocol.OPENAI_RESPONSES -> OpenAiChatAdapter(client, json) // TODO(阶段 6): OpenAiResponsesAdapter
        LlmProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesAdapter(client, json)
    }
}
