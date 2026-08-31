package com.voiceink.app.update

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer()
    private val checker = UpdateChecker(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `新版本号大于当前版本`() {
        assertTrue(checker.isNewer("0.2.0", "0.1.1"))
        assertTrue(checker.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `解析最新 release 的说明和 APK 直链`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "tag_name":"v0.2.0",
                  "body":"修复设置页联调问题",
                  "html_url":"https://github.com/Dongyurocket/shengnian/releases/tag/v0.2.0",
                  "assets":[
                    {"name":"source.zip","browser_download_url":"https://example.com/source.zip"},
                    {"name":"shengnian-v0.2.0.apk","browser_download_url":"https://example.com/shengnian.apk"}
                  ]
                }"""
            )
        )

        val info = checker.checkAt(server.url("/releases/latest").toString(), "0.1.1")

        assertNotNull(info)
        assertEquals("0.2.0", info!!.version)
        assertEquals("修复设置页联调问题", info.notes)
        assertEquals("https://example.com/shengnian.apk", info.apkUrl)
        assertEquals(
            "https://github.com/Dongyurocket/shengnian/releases/tag/v0.2.0",
            info.pageUrl
        )
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
    }

    @Test
    fun `当前版本与最新 release 相同时不返回更新`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v0.1.1","body":null}"""))
        assertNull(checker.checkAt(server.url("/").toString(), "0.1.1"))
    }

    @Test
    fun `相同或更旧版本不提示更新`() {
        assertFalse(checker.isNewer("0.1.1", "0.1.1"))
        assertFalse(checker.isNewer("0.1.0", "0.1.1"))
    }

    @Test
    fun `缺少 patch 时按零补齐`() {
        assertTrue(checker.isNewer("0.2", "0.1.9"))
        assertFalse(checker.isNewer("0.1", "0.1.0"))
    }
}
