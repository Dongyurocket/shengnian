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
    fun `todo alarm flag is parsed separately from a regular reminder`() {
        val alarm = JsonExtractor.extractIntent(
            """{"intent":"todo","content":"起床","priority":1,"deadline":"2030-01-15 07:50","is_alarm":true,"remind_lead_minutes":-1}"""
        ) as ParsedIntent.Todo
        assertTrue(alarm.isAlarm)
        assertNull(alarm.remindLeadMinutes)
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
    fun `strict schema 的占位字段不会变成空元数据或提醒`() {
        val note = JsonExtractor.extractIntent(
            """{"intent":"note","title":"t","content":"c","category":"","type":"","mood":"","tags":[],"summary":"","todos":[],"priority":0,"deadline":"","remind_lead_minutes":0}"""
        )
        assertTrue(note is ParsedIntent.Note)
        note as ParsedIntent.Note
        assertNull(note.category)
        assertNull(note.type)
        assertNull(note.mood)
        assertNull(note.summary)

        val todo = JsonExtractor.extractIntent(
            """{"intent":"todo","title":"","content":"c","category":"","type":"","mood":"","tags":[],"summary":"","todos":[],"priority":1,"deadline":"","remind_lead_minutes":-1}"""
        )
        assertTrue(todo is ParsedIntent.Todo)
        assertNull((todo as ParsedIntent.Todo).deadline)
        assertNull(todo.remindLeadMinutes)
    }

    @Test
    fun `笔记意图可提炼待办`() {
        val raw = """{"intent":"note","title":"t","content":"c","todos":["关掉实验分支","回复授权邮件"]}"""
        val r = JsonExtractor.extractIntent(raw)
        assertTrue(r is ParsedIntent.Note)
        assertEquals(listOf("关掉实验分支", "回复授权邮件"), (r as ParsedIntent.Note).todos)
    }

    @Test
    fun `灵感标记优先读取布尔字段并兼容旧类型`() {
        val explicit = JsonExtractor.extractIntent(
            """{"intent":"note","title":"t","content":"c","type":"总结","is_inspiration":true}"""
        ) as ParsedIntent.Note
        assertTrue(explicit.isInspiration)

        val legacy = JsonExtractor.extractIntent(
            """{"intent":"note","title":"t","content":"c","type":"灵感"}"""
        ) as ParsedIntent.Note
        assertTrue(legacy.isInspiration)
    }


    @Test
    fun `结构化字段限制长度数量并过滤未知枚举`() {
        val tags = (1..20).joinToString(",") { "\"${"t".repeat(50)}$it\"" }
        val todos = (1..6).joinToString(",") { "\"${"todo".repeat(100)}$it\"" }
        val note = JsonExtractor.extractIntent(
            """{"intent":"note","title":"${"标题".repeat(200)}","content":"正文","type":"未知","mood":"未知","tags":[$tags],"todos":[$todos]}"""
        ) as ParsedIntent.Note
        assertTrue(note.title.length <= 180)
        assertNull(note.type)
        assertNull(note.mood)
        assertEquals(8, note.tags.size)
        assertEquals(3, note.todos.size)
        assertTrue(note.todos.all { it.length <= 240 })
    }

    @Test
    fun `关联复核输出解析`() {
        val r = JsonExtractor.extractLinks("""{"related":[{"id":17,"reason":"同一项目"},{"id":9}]}""")
        assertEquals(listOf(17L to "同一项目", 9L to ""), r)
        assertEquals(emptyList<Pair<Long, String>>(), JsonExtractor.extractLinks("""{"related":[]}"""))
        assertEquals(emptyList<Pair<Long, String>>(), JsonExtractor.extractLinks("不是 JSON"))
    }
}
