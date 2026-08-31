package com.voiceink.app.ai

/** 三协议枚举（§7.1） */
enum class LlmProtocol(val label: String) {
    OPENAI_CHAT("OpenAI Chat Completions"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC_MESSAGES("Anthropic Messages")
}

data class LlmEndpoint(
    val baseUrl: String,          // 用户填写，如 https://api.openai.com 或 http://192.168.1.5:11434
    val apiKey: String,
    val model: String,
    val protocol: LlmProtocol
)

/** Embedding 独立配置：与聊天 LLM 解耦，可指向任何 OpenAI 兼容 /v1/embeddings 服务 */
data class EmbeddingEndpoint(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
)

/** 内部标准请求：一次"系统指令 + 用户输入 → JSON 文本"的调用 */
data class LlmRequest(
    val system: String,
    val user: String,
    val jsonSchemaName: String,   // 供 Responses json_schema / 兜底解析使用
    val maxTokens: Int = 2048,
    val temperature: Double = 0.3
)

/** 内部标准结果：只承诺 text 是"尽量合法 JSON 的字符串"，解析兜底在 JsonExtractor */
data class LlmResult(val text: String, val stopReason: StopReason, val usageTokens: Int?)

enum class StopReason { COMPLETE, MAX_TOKENS, OTHER }
