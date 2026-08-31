package com.voiceink.app.ai.prompt

import com.voiceink.app.core.TimeUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON 兜底解析（§8.3）：即使协议声明了 JSON 约束，中转代理/小模型仍可能输出杂质。
 * 处理：```json 包裹、前后废话、Anthropic 预填拼接、字符串内含花括号。
 */
object JsonExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    fun extractIntent(raw: String): ParsedIntent {
        val obj = firstJsonObject(raw) ?: return ParsedIntent.Unparseable
        return runCatching { decodeIntent(obj) }.getOrElse { ParsedIntent.Unparseable }
    }

    private fun decodeIntent(o: JsonObject): ParsedIntent {
        return when (o["intent"]?.jsonPrimitive?.contentOrNull) {
            "todo" -> {
                val content = o["content"]?.jsonPrimitive?.contentOrNull
                    ?: return ParsedIntent.Unparseable
                ParsedIntent.Todo(
                    content = content,
                    priority = o["priority"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 2) ?: 1,
                    deadline = o["deadline"]?.jsonPrimitive?.contentOrNull
                        ?.let { TimeUtils.parseDateTime(it) },
                    remindLeadMinutes = o["remind_lead_minutes"]?.jsonPrimitive?.intOrNull
                        ?.coerceIn(0, 24 * 60)
                )
            }
            "note" -> ParsedIntent.Note(
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                category = o["category"]?.jsonPrimitive?.contentOrNull,
                type = o["type"]?.jsonPrimitive?.contentOrNull,
                mood = o["mood"]?.jsonPrimitive?.contentOrNull,
                tags = o["tags"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                summary = o["summary"]?.jsonPrimitive?.contentOrNull,
                todos = o["todos"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            )
            else -> ParsedIntent.Unparseable
        }
    }

    /** 解析关联复核输出：{"related":[{"id":17,"reason":"…"}]} → List<Pair<id, reason>> */
    fun extractLinks(raw: String): List<Pair<Long, String>> {
        val obj = firstJsonObject(raw) ?: return emptyList()
        val arr = obj["related"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
            id to reason
        }
    }

    /** 从任意文本中抠出第一个完整 JSON 对象（处理 ```json 包裹、前后废话、预填缺 '{'） */
    fun firstJsonObject(raw: String): JsonObject? {
        var s = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (!s.startsWith("{")) s = "{" + s.substringAfter('{', s)   // 预填场景：无 '{' 时保留全串
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr && --depth == 0) {
                    return runCatching {
                        json.parseToJsonElement(s.substring(start, i + 1)).jsonObject
                    }.getOrNull()
                }
            }
        }
        return null
    }
}
