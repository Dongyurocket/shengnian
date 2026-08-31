package com.voiceink.app.ai.prompt

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PromptsSchemaTest {
    @Test
    fun `intent schema 的数组字段包含合法 item schema`() {
        val properties = Prompts.schemaFor("intent")["properties"]!!.jsonObject
        val tags = properties["tags"]!!.jsonObject
        val todos = properties["todos"]!!.jsonObject

        assertEquals("array", tags["type"]!!.jsonPrimitive.content)
        assertEquals("string", tags["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("array", todos["type"]!!.jsonPrimitive.content)
        assertEquals("string", todos["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `link schema 的 related 是对象数组`() {
        val related = Prompts.schemaFor("link")["properties"]!!.jsonObject["related"]!!.jsonObject
        assertEquals("array", related["type"]!!.jsonPrimitive.content)
        assertEquals("object", related["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNotNull(Prompts.schemaFor("link")["required"])
    }
}
