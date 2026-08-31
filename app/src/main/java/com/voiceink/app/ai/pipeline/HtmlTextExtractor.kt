package com.voiceink.app.ai.pipeline

/** HTML 清理后的标题与主正文。 */
data class ExtractedHtml(val title: String, val text: String)

/**
 * 面向个人笔记的受限正文提取器。它不执行脚本，也不保留 HTML；优先 article/main，
 * 找不到有效正文时退回 body，并把常见块标签转换为换行。
 */
object HtmlTextExtractor {
    private val titleRegex = Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
    private val metaTagRegex = Regex("""(?is)<meta\b[^>]*>""")
    private val attributeRegex = Regex("""(?is)([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(['"])(.*?)\2""")
    private val mainRegex = Regex("""(?is)<(?:article|main)\b[^>]*>(.*?)</(?:article|main)>""")
    private val bodyRegex = Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
    private val removableRegex = Regex(
        """(?is)<(?:script|style|noscript|svg|nav|footer|form|aside|header)\b[^>]*>.*?</(?:script|style|noscript|svg|nav|footer|form|aside|header)>"""
    )
    private val commentRegex = Regex("""(?s)<!--.*?-->""")
    private val blockRegex = Regex(
        """(?is)</?(?:p|div|section|article|main|header|h[1-6]|li|ul|ol|blockquote|table|tr|pre|br)\b[^>]*>"""
    )
    private val tagRegex = Regex("""(?s)<[^>]+>""")
    private val whitespaceRegex = Regex("""[\t\x0B\f\r ]+""")
    private val blankLinesRegex = Regex("""\n\s*\n+""")

    fun extract(html: String, maxChars: Int = 6_000): ExtractedHtml {
        val withoutComments = html.replace(commentRegex, " ")
        val cleaned = withoutComments.replace(removableRegex, " ")
        val title = decodeEntities(
            metaTitle(cleaned)
                ?: titleRegex.find(cleaned)?.groupValues?.getOrNull(1).orEmpty()
        ).let(::normalizeInline).take(180)

        val main = mainRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val body = bodyRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val text = sequenceOf(main, body, cleaned)
            .filterNotNull()
            .map(::cleanText)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .take(maxChars.coerceAtLeast(0))

        return ExtractedHtml(title, text)
    }

    private fun metaTitle(html: String): String? = metaTagRegex.findAll(html)
        .mapNotNull { tag ->
            val attrs = attributeRegex.findAll(tag.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[3]
            }
            val property = attrs["property"]?.lowercase() ?: attrs["name"]?.lowercase()
            if (property == "og:title" || property == "twitter:title") attrs["content"] else null
        }
        .firstOrNull { it.isNotBlank() }

    private fun cleanText(fragment: String): String = decodeEntities(
        fragment.replace(blockRegex, "\n").replace(tagRegex, " ")
    )
        .lines()
        .map { normalizeInline(it) }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .replace(blankLinesRegex, "\n")
        .trim()

    private fun normalizeInline(value: String): String =
        value.replace(whitespaceRegex, " ").trim()

    internal fun decodeEntities(value: String): String {
        var s = value
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
        s = Regex("""&#(\d+);""").replace(s) { match ->
            match.groupValues[1].toIntOrNull()?.let(::codePoint) ?: match.value
        }
        s = Regex("""&#x([0-9a-fA-F]+);""").replace(s) { match ->
            match.groupValues[1].toIntOrNull(16)?.let(::codePoint) ?: match.value
        }
        return s
    }

    private fun codePoint(value: Int): String = runCatching {
        String(Character.toChars(value))
    }.getOrDefault("")
}
