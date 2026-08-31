package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.LlmResult

/** 协议适配器接口（§7.1）：上层只面向 LlmRequest/LlmResult 编程 */
interface LlmAdapter {
    val protocol: LlmProtocol

    /** 根据端点配置构建 HTTP 请求体/头，并把响应解析为 LlmResult */
    suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult
}
