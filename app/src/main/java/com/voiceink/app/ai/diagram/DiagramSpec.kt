package com.voiceink.app.ai.diagram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 图表类型；模型只能返回这两种受控结构。 */
enum class DiagramKind(val wireName: String, val label: String) {
    FLOWCHART("flowchart", "流程图"),
    MINDMAP("mindmap", "思维导图");

    companion object {
        fun fromWire(value: String?): DiagramKind? = entries.firstOrNull { it.wireName == value }
    }
}

data class DiagramNode(
    val id: String,
    val label: String,
    val shape: String = "rect"
)

data class DiagramEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

data class DiagramSpec(
    val kind: DiagramKind,
    val title: String,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>
) {
    fun toJson(): String = buildJsonObject {
        put("kind", kind.wireName)
        put("title", title)
        putJsonArray("nodes") {
            nodes.forEach { node ->
                add(buildJsonObject {
                    put("id", node.id)
                    put("label", node.label)
                    put("shape", node.shape)
                })
            }
        }
        putJsonArray("edges") {
            edges.forEach { edge ->
                add(buildJsonObject {
                    put("from", edge.from)
                    put("to", edge.to)
                    put("label", edge.label)
                })
            }
        }
    }.toString()

    companion object {
        fun fromJson(raw: String): DiagramSpec? =
            com.voiceink.app.ai.prompt.JsonExtractor.extractDiagram(raw)
    }
}

/** 仅用于让调用方在需要时验证存储的 JSON 可被解析。 */
fun diagramJsonIsValid(raw: String): Boolean = DiagramSpec.fromJson(raw) != null
