package com.voiceink.app.ai.prompt

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `DeepSeek strict schema 的每个对象都要求完整属性且禁止额外字段`() {
        assertStrictObject(Prompts.schemaFor("intent"))

        val link = Prompts.schemaFor("link")
        assertStrictObject(link)
        val related = link["properties"]!!.jsonObject["related"]!!.jsonObject
        assertStrictObject(related["items"]!!.jsonObject)
    }

    private fun assertStrictObject(schema: kotlinx.serialization.json.JsonObject) {
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        val properties = schema["properties"]!!.jsonObject
        val required = schema["required"]!!.jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()
        assertEquals(properties.keys, required)
        assertFalse(schema["additionalProperties"]!!.jsonPrimitive.boolean)
    }
}
