package com.voiceink.app.ai.prompt

import com.voiceink.app.ai.diagram.DiagramEdge
import com.voiceink.app.ai.diagram.DiagramKind
import com.voiceink.app.ai.diagram.DiagramNode
import com.voiceink.app.ai.diagram.DiagramSpec
import com.voiceink.app.core.TimeUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
    private val VALID_SHAPES = setOf("root", "rect", "decision")
    private val VALID_TYPES = setOf("灵感", "总结", "摘录", "待研究", "日记")
    private val VALID_MOODS = setOf("积极", "中立", "消极")

    fun extractIntent(raw: String): ParsedIntent {
        val obj = firstJsonObject(raw) ?: return ParsedIntent.Unparseable
        return runCatching { decodeIntent(obj) }.getOrElse { ParsedIntent.Unparseable }
    }

    private fun decodeIntent(o: JsonObject): ParsedIntent {
        return when (o["intent"]?.jsonPrimitive?.contentOrNull) {
            "todo" -> {
                val content = o["content"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.take(MAX_TODO_CHARS)
                    ?.takeIf { it.isNotBlank() }
                    ?: return ParsedIntent.Unparseable
                ParsedIntent.Todo(
                    content = content,
                    priority = o["priority"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 2) ?: 1,
                    deadline = o["deadline"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { TimeUtils.parseDateTime(it) },
                    remindLeadMinutes = o["remind_lead_minutes"]?.jsonPrimitive?.intOrNull
                        ?.takeIf { it >= 0 }
                        ?.coerceIn(0, 24 * 60)
                )
            }
            "note" -> {
                val type = optionalText(o, "type", 40)
                val mood = optionalText(o, "mood", 40)
                ParsedIntent.Note(
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty().take(180),
                    content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty().take(MAX_NOTE_CHARS),
                    category = optionalText(o, "category", 80),
                    type = type?.takeIf { it in VALID_TYPES },
                    mood = mood?.takeIf { it in VALID_MOODS },
                    tags = o["tags"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                        ?.take(MAX_TAGS)
                        ?.map { it.take(MAX_TAG_CHARS) }
                        ?: emptyList(),
                    summary = optionalText(o, "summary", MAX_SUMMARY_CHARS),
                    todos = o["todos"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                        ?.take(MAX_TODOS)
                        ?.map { it.take(MAX_TODO_CHARS) }
                        ?: emptyList(),
                    isInspiration = o["is_inspiration"]?.jsonPrimitive?.booleanOrNull
                        ?: (type == "灵感")
                )
            }
            else -> ParsedIntent.Unparseable
        }
    }

    private fun optionalText(o: JsonObject, key: String, maxChars: Int = MAX_TEXT_CHARS): String? =
        o[key]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(maxChars)

    /** 解析关联复核输出：{"related":[{"id":17,"reason":"…"}]} → List<Pair<id, reason>> */
    fun extractLinks(raw: String): List<Pair<Long, String>> {
        val obj = firstJsonObject(raw) ?: return emptyList()
        val arr = obj["related"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?.takeIf { it > 0 } ?: return@mapNotNull null
            val reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .trim().take(MAX_REASON_CHARS)
            id to reason
        }
    }

    /** 解析并校验流程图/思维导图 JSON，拒绝悬空边和超大结构。 */
    fun extractDiagram(raw: String): DiagramSpec? {
        val obj = firstJsonObject(raw) ?: return null
        val kind = DiagramKind.fromWire(obj["kind"]?.jsonPrimitive?.contentOrNull) ?: return null
        val nodes = obj["nodes"]?.jsonArray?.mapNotNull { item ->
            val node = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .take(MAX_NODE_ID_CHARS)
            val label = node["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val shape = node["shape"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val normalizedShape = shape.ifBlank { "rect" }
            if (id.isBlank() || label.isBlank() || normalizedShape !in VALID_SHAPES) null
            else DiagramNode(id, label.take(80), normalizedShape)
        } ?: return null
        if (nodes.isEmpty() || nodes.size > 12 || nodes.map { it.id }.toSet().size != nodes.size) return null

        val ids = nodes.map { it.id }.toSet()
        val edges = obj["edges"]?.jsonArray?.mapNotNull { item ->
            val edge = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val from = edge["from"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val to = edge["to"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (from !in ids || to !in ids || from == to) null
            else DiagramEdge(
                from = from,
                to = to,
                label = edge["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().take(40)
            )
        } ?: emptyList()
        if (edges.size > 16) return null
        return DiagramSpec(
            kind = kind,
            title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.take(80).orEmpty().ifBlank { kind.label },
            nodes = nodes,
            edges = edges.distinctBy { it.from to it.to }
        )
    }

    /** 从任意文本中抠出第一个完整 JSON 对象（处理 ```json 包裹、前后废话、预填缺 '{'） */
    fun firstJsonObject(raw: String): JsonObject? {
        var s = raw.trim()
            .removePrefix("\uFEFF")
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

    private const val MAX_NOTE_CHARS = 20_000
    private const val MAX_TEXT_CHARS = 500
    private const val MAX_TAGS = 8
    private const val MAX_TAG_CHARS = 40
    private const val MAX_TODOS = 3
    private const val MAX_TODO_CHARS = 240
    private const val MAX_SUMMARY_CHARS = 500
    private const val MAX_REASON_CHARS = 200
    private const val MAX_NODE_ID_CHARS = 64
}
