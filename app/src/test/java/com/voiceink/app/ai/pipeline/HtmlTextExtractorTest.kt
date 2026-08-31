package com.voiceink.app.ai.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextExtractorTest {
    @Test
    fun `优先 article 并移除脚本导航与实体`() {
        val html = """
            <html><head><title>示例 &amp; 标题</title><script>bad()</script></head>
            <body><nav>菜单</nav><article><h1>主标题</h1><p>第一段&nbsp;内容。</p><p>第二段</p></article><footer>页脚</footer></body></html>
        """.trimIndent()
        val result = HtmlTextExtractor.extract(html)
        assertEquals("示例 & 标题", result.title)
        assertTrue(result.text.contains("主标题"))
        assertTrue(result.text.contains("第一段 内容。"))
        assertFalse(result.text.contains("菜单"))
        assertFalse(result.text.contains("bad"))
    }

    @Test
    fun `支持 meta 属性任意顺序并优先使用社交标题`() {
        val html = """
            <meta content='社交标题' name='twitter:title'>
            <title>普通标题</title>
            <body><p>正文</p></body>
        """.trimIndent()
        assertEquals("社交标题", HtmlTextExtractor.extract(html).title)
    }

    @Test
    fun `正文按上限截断且空页面安全`() {
        assertEquals("12345", HtmlTextExtractor.extract("<body>123456789</body>", 5).text)
        assertTrue(HtmlTextExtractor.extract("<html><body><script>x</script></body></html>").text.isBlank())
    }
}
