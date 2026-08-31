package com.voiceink.app.ai.prompt

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 全部 System Prompt 与 JSON Schema（§8.2，内置，支持后续用户自定义覆写） */
object Prompts {

    val INTENT_AND_ORGANIZE = """
你是一个个人笔记整理助手。用户会提供一段输入文本（可能来自语音输入法，可能有口语、重复、识别错字）。
请先纠正明显错字，再判断意图并只输出一个 JSON 对象，不要输出任何其他文字。

意图 A：灵感/想法/随笔/记录 → 输出：
{
  "intent": "note",
  "title": "≤15字的精准标题",
  "content": "整理后的正文（保留原意，去除口头禅，分段清晰）",
  "category": "主题分类，从用户常用分类中选，都不合适才新建",
  "type": "灵感|总结|摘录|待研究|日记 之一",
  "mood": "积极|中立|消极",
  "tags": ["3-5个精准关键词"],
  "summary": "一句话摘要",
  "todos": ["从正文中提炼出的可执行待办，0-3条，纯内容字符串，无则省略该字段"]
}
意图 B：待办/计划/提醒 → 输出：
{
  "intent": "todo",
  "content": "任务内容（动宾结构，可执行）",
  "priority": 0或1或2,
  "deadline": "yyyy-MM-dd HH:mm，无明确时间则省略该字段",
  "remind_lead_minutes": 提前提醒分钟数，用户未指定则省略
}
时间词（明天/下周三/下班前）一律以用户提供的“当前时间”为基准换算成绝对时间。
""".trimIndent()

    /** 关联复核（§9.1③）：宁缺毋滥 */
    val LINK_JUDGE = """
你在做个人笔记的语义关联复核。给定一条新笔记（标题+摘要）和若干候选笔记（id/标题/摘要），
判断哪些候选与新笔记存在真实的语义关联（同一主题的延续、同一项目、可互相印证的想法）。
只输出 JSON：{"related":[{"id":数字,"reason":"一句话说明关联点"}]}，无关联输出 {"related":[]}。
宁缺毋滥：只有确有把握关联时才输出。
""".trimIndent()

    /** 供 OpenAI Responses json_schema strict 使用（阶段 6） */
    fun schemaFor(name: String): JsonObject = when (name) {
        "intent" -> INTENT_JSON_SCHEMA
        "link" -> LINK_JSON_SCHEMA
        else -> JsonObject(emptyMap())
    }

    private val LINK_JSON_SCHEMA = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("related") {
                put("type", "array")
                putJsonObject("items") { put("type", "object") }
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("related")) }
        put("additionalProperties", true)
    }

    private val INTENT_JSON_SCHEMA = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("intent") {
                put("type", "string")
                putJsonArray("enum") {
                    add(kotlinx.serialization.json.JsonPrimitive("note"))
                    add(kotlinx.serialization.json.JsonPrimitive("todo"))
                }
            }
            putJsonObject("title") { put("type", "string") }
            putJsonObject("content") { put("type", "string") }
            putJsonObject("category") { put("type", "string") }
            putJsonObject("type") { put("type", "string") }
            putJsonObject("mood") { put("type", "string") }
            putJsonObject("tags") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("summary") { put("type", "string") }
            putJsonObject("todos") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("priority") { put("type", "integer") }
            putJsonObject("deadline") { put("type", "string") }
            putJsonObject("remind_lead_minutes") { put("type", "integer") }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("intent")) }
        put("additionalProperties", true)
    }
}
