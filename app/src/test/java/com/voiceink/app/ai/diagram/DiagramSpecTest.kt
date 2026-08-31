package com.voiceink.app.ai.diagram

import com.voiceink.app.ai.prompt.JsonExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagramSpecTest {
    private val valid = """
        {"kind":"flowchart","title":"发布流程",
         "nodes":[{"id":"a","label":"提出想法","shape":"root"},{"id":"b","label":"验证","shape":"rect"}],
         "edges":[{"from":"a","to":"b","label":"然后"}]}
    """.trimIndent()

    @Test
    fun `解析合法图表并可序列化`() {
        val spec = JsonExtractor.extractDiagram(valid)
        assertNotNull(spec)
        assertEquals(DiagramKind.FLOWCHART, spec!!.kind)
        assertEquals(2, spec.nodes.size)
        assertEquals(validKindJson(spec), DiagramSpec.fromJson(spec.toJson())?.toJson())
    }

    @Test
    fun `拒绝重复节点和悬空边`() {
        val duplicate = valid.replace("{\"id\":\"b\"", "{\"id\":\"a\"")
        assertNull(JsonExtractor.extractDiagram(duplicate))
        val dangling = valid.replace("\"to\":\"b\"", "\"to\":\"missing\"")
        val parsed = JsonExtractor.extractDiagram(dangling)
        assertNotNull(parsed)
        assertFalse(parsed!!.edges.any { it.to == "missing" })
    }

    @Test
    fun `兼容代码块并限制节点数量`() {
        val wrapped = "```json\n${valid}\n```"
        assertNotNull(JsonExtractor.extractDiagram(wrapped))
        val tooMany = (1..13).joinToString(",") {
            "{\"id\":\"n$it\",\"label\":\"$it\",\"shape\":\"rect\"}"
        }
        assertNull(
            JsonExtractor.extractDiagram(
                "{\"kind\":\"mindmap\",\"title\":\"x\",\"nodes\":[$tooMany],\"edges\":[]}"
            )
        )
    }

    private fun validKindJson(spec: DiagramSpec): String = spec.toJson()
}
