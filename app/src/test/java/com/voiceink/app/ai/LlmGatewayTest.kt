package com.voiceink.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmGatewayTest {

    @Test
    fun `非 JSON 文本的 HTTP 成功响应仍显示连接成功`() {
        assertEquals("连接成功", connectionTestMessage("模型已响应，但不是业务 JSON"))
    }

    @Test
    fun `空响应显示已连接但提示模型未返回内容`() {
        assertEquals("连接成功（模型未返回内容）", connectionTestMessage("  "))
    }
}
