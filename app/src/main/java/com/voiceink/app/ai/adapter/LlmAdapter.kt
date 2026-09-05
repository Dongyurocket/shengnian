package com.voiceink.app.ai.adapter

import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.LlmResult

/** 流式响应中的标准化事件；正文始终在 adapter 内聚合并最终以 LlmResult 返回。 */
sealed interface LlmStreamEvent {
    /** 服务端已返回成功响应头，正在等待或读取模型输出。 */
    data object Connected : LlmStreamEvent

    /** 正式输出的增量，只用于推进 UI 阶段，不直接持久化或渲染半截 JSON。 */
    data class TextDelta(val text: String) : LlmStreamEvent

    /** 提供商明确标记为 summary 的推理摘要；不接收原始 chain-of-thought。 */
    data class ReasoningSummaryDelta(val text: String) : LlmStreamEvent
}

/** 协议适配器接口（§7.1）：上层只面向 LlmRequest/LlmResult 编程 */
interface LlmAdapter {
    val protocol: LlmProtocol

    /** 根据端点配置构建 HTTP 请求体/头，并把响应解析为 LlmResult */
    suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult

    /**
     * 请求 SSE 流，同时仍返回完整的结构化输出，避免上层处理半截 JSON。
     * 仅在调用方明确提供回调时触发阶段和可选推理摘要事件。
     */
    suspend fun completeStreaming(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        onEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResult
}
