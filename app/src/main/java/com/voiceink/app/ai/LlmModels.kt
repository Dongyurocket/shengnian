package com.voiceink.app.ai

/** 发送给视觉模型的已编码图片；base64 只存在于一次请求的内存中。 */
data class LlmImage(
    val mimeType: String,
    val base64: String
) {
    fun dataUrl(): String = "data:${mimeType.ifBlank { "image/jpeg" }};base64,$base64"
}

/** 三协议枚举（§7.1） */
enum class LlmProtocol(val label: String) {
    OPENAI_CHAT("OpenAI Chat Completions"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC_MESSAGES("Anthropic Messages")
}

enum class ThinkingEffort(
    val wire: String,
    val label: String,
    val budgetTokens: Int
) {
    LOW("low", "低", 1024),
    MEDIUM("medium", "中", 2048),
    HIGH("high", "高", 4096)
}

data class LlmEndpoint(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val protocol: LlmProtocol,
    val thinkingEnabled: Boolean = false,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM
)

data class EmbeddingEndpoint(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
)

/** 内部标准请求：文本可附带图片，协议适配器负责转换内容块。 */
data class LlmRequest(
    val system: String,
    val user: String,
    val jsonSchemaName: String,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.3,
    val images: List<LlmImage> = emptyList()
)

data class LlmResult(val text: String, val stopReason: StopReason, val usageTokens: Int?)

enum class StopReason { COMPLETE, MAX_TOKENS, OTHER }
