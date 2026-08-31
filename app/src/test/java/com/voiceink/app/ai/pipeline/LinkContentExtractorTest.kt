package com.voiceink.app.ai.pipeline

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkContentExtractorTest {
    @Test
    fun `相对重定向后提取 HTML 正文`() = runTest {
        val requests = AtomicInteger(0)
        val client = testClient { chain ->
            requests.incrementAndGet()
            if (chain.request().url.encodedPath == "/start") {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "/final")
                    .body("".toResponseBody())
                    .build()
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body("<html><head><meta property='og:title' content='标题'></head><article><p>正文内容</p></article></html>".toResponseBody())
                    .build()
            }
        }

        val result = LinkContentExtractor(client).fetch("https://8.8.8.8/start")

        assertTrue(result is LinkFetchResult.Success)
        assertEquals("标题", (result as LinkFetchResult.Success).title)
        assertTrue(result.text.contains("正文内容"))
        assertEquals(2, requests.get())
    }

    @Test
    fun `HTTPS 不跟随降级到本机 HTTP`() = runTest {
        val requests = AtomicInteger(0)
        val client = testClient { chain ->
            requests.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "http://127.0.0.1/private")
                .body("".toResponseBody())
                .build()
        }

        val result = LinkContentExtractor(client).fetch("https://8.8.8.8/start")

        assertTrue(result is LinkFetchResult.Unsupported)
        assertTrue((result as LinkFetchResult.Unsupported).reason.contains("降级"))
        assertEquals(1, requests.get())
    }

    private fun testClient(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
}
