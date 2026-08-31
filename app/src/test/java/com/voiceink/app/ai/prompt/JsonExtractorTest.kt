package com.voiceink.app.ai.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON 兜底解析五种脏输入场景（§8.3） */
class JsonExtractorTest {

    private val noteJson = """{"intent":"note","title":"标题","content":"正文","tags":["a","b"],"summary":"摘要"}"""

    @Test
    fun `纯 JSON`() {
        val r = JsonExtractor.extractIntent(noteJson)
        assertTrue(r is ParsedIntent.Note)
        r as ParsedIntent.Note
        assertEquals("标题", r.title)
        assertEquals(listOf("a", "b"), r.tags)
    }

    @Test
    fun `markdown 代码块包裹`() {
        val r = JsonExtractor.extractIntent("```json\n$noteJson\n```")
        assertTrue(r is ParsedIntent.Note)
    }

    @Test
    fun `前后带废话`() {
        val r = JsonExtractor.extractIntent("好的，以下是整理结果：\n$noteJson\n希望对你有帮助！")
        assertTrue(r is ParsedIntent.Note)
        assertEquals("正文", (r as ParsedIntent.Note).content)
    }

    @Test
    fun `预填缺左花括号`() {
        // Anthropic 预填场景：模型从 '{' 后续写，文本丢失了开头
        val partial = noteJson.substring(1)
        val r = JsonExtractor.extractIntent(partial)
        assertTrue(r is ParsedIntent.Note)
        assertEquals("标题", (r as ParsedIntent.Note).title)
    }

    @Test
    fun `字符串内含右花括号不提前截断`() {
        val tricky = """{"intent":"note","title":"a}b","content":"包含 } 的花括号","tags":[],"summary":""}"""
        val obj = JsonExtractor.firstJsonObject(tricky)
        assertNotNull(obj)
        assertEquals("a}b", obj!!["title"]!!.let { it.toString().trim('"') })
    }

    @Test
    fun `todo 意图解析与时间换算`() {
        val todoJson = """{"intent":"todo","content":"把周报发给王总","priority":2,"deadline":"2030-01-15 15:00"}"""
        val r = JsonExtractor.extractIntent(todoJson)
        assertTrue(r is ParsedIntent.Todo)
        r as ParsedIntent.Todo
        assertEquals("把周报发给王总", r.content)
        assertEquals(2, r.priority)
        assertNotNull(r.deadline)
    }

    @Test
    fun `无法解析返回 Unparseable`() {
        assertEquals(ParsedIntent.Unparseable, JsonExtractor.extractIntent("这根本不是 JSON"))
        assertEquals(ParsedIntent.Unparseable, JsonExtractor.extractIntent("""{"foo":"bar"}"""))
    }

    @Test
    fun `todo 缺 deadline 字段可省略`() {
        val r = JsonExtractor.extractIntent("""{"intent":"todo","content":"随便买点水果"}""")
        assertTrue(r is ParsedIntent.Todo)
        assertNull((r as ParsedIntent.Todo).deadline)
    }

    @Test
    fun `笔记意图可提炼待办`() {
        val raw = """{"intent":"note","title":"t","content":"c","todos":["关掉实验分支","回复授权邮件"]}"""
        val r = JsonExtractor.extractIntent(raw)
        assertTrue(r is ParsedIntent.Note)
        assertEquals(listOf("关掉实验分支", "回复授权邮件"), (r as ParsedIntent.Note).todos)
    }
}
