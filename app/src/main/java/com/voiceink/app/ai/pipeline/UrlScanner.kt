package com.voiceink.app.ai.pipeline

import java.net.URI

/** 从用户文本中提取少量 HTTP(S) URL，避免把尾部自然语言标点带入请求。 */
object UrlScanner {
    private val urlRegex = Regex("""https?://[^\s<>\"'，。；：！？）】》]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}',
        '。', '，', '；', '：', '！', '？', '）', '】', '》', '…',
        '”', '’', '、'
    )

    fun extract(text: String, maxUrls: Int = 3): List<String> =
        urlRegex.findAll(text)
            .map { it.value.trimEnd(*trailingPunctuation) }
            .filter(::isSupported)
            .distinct()
            .take(maxUrls.coerceAtLeast(0))
            .toList()

    private fun isSupported(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            uri.userInfo.isNullOrBlank() &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
