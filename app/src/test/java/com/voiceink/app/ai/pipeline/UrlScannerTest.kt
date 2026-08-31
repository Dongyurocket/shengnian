package com.voiceink.app.ai.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlScannerTest {
    @Test
    fun `提取去重并移除尾部标点`() {
        val text = "看 https://example.com/a?q=1，另一个 https://example.org/path。重复 https://example.com/a?q=1"
        assertEquals(
            listOf("https://example.com/a?q=1", "https://example.org/path"),
            UrlScanner.extract(text)
        )
    }

    @Test
    fun `去除中文省略号并拒绝非法协议`() {
        assertEquals(
            listOf("https://example.com/article"),
            UrlScanner.extract("https://example.com/article…… javascript:alert(1)")
        )
    }

    @Test
    fun `拒绝带登录凭据的网页地址`() {
        assertEquals(emptyList<String>(), UrlScanner.extract("https://user:pass@example.com/private"))
    }

    @Test
    fun `只接受 http 和 https 且限制数量`() {
        val text = "ftp://bad.test/a http://one.test https://two.test https://three.test"
        assertEquals(
            listOf("http://one.test", "https://two.test"),
            UrlScanner.extract(text, maxUrls = 2)
        )
    }
}
