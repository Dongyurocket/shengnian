package com.voiceink.app.ai.pipeline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LinkFetchResult {
    data class Success(val title: String, val text: String) : LinkFetchResult
    data class Unsupported(val reason: String) : LinkFetchResult
    data class Failure(val reason: String) : LinkFetchResult
}

/** 受限网页下载：只读取 HTML/纯文本，限制响应大小、超时和重定向目标。 */
@Singleton
class LinkContentExtractor @Inject constructor(client: OkHttpClient) {
    private val http = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun fetch(url: String): LinkFetchResult = withContext(Dispatchers.IO) {
        var current = url
        var redirects = 0
        while (redirects <= MAX_REDIRECTS) {
            val target = try {
                URI(current)
            } catch (_: Exception) {
                return@withContext LinkFetchResult.Failure("链接格式无效")
            }
            val resolution = LinkUrlPolicy.resolve(target)
                ?: return@withContext LinkFetchResult.Unsupported(
                    LinkUrlPolicy.blockReason(target) ?: "不允许访问此地址"
                )

            when (val attempt = fetchOnce(current, target, resolution)) {
                is FetchAttempt.Complete -> return@withContext attempt.result
                is FetchAttempt.Redirect -> {
                    current = attempt.url
                    redirects += 1
                }
            }
        }
        LinkFetchResult.Failure("页面重定向次数过多")
    }

    private fun fetchOnce(
        current: String,
        target: URI,
        resolution: AllowedUrlHost
    ): FetchAttempt {
        return try {
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", "ShengNian/0.3 (+Android note extractor)")
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.8")
                .get()
                .build()
            val requestClient = http.newBuilder()
                // 网页来源必须直连，并固定本次策略检查得到的地址，避免代理或 DNS rebinding 绕过检查。
                .proxy(Proxy.NO_PROXY)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .cookieJar(CookieJar.NO_COOKIES)
                .dns(object : Dns {
                    override fun lookup(host: String): List<InetAddress> =
                        if (normalizeHost(host) == resolution.host) {
                            resolution.addresses
                        } else {
                            throw UnknownHostException(host)
                        }
                })
                .build()
            requestClient.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val location = response.header("Location")
                        ?.takeIf { it.length <= MAX_LOCATION_CHARS }
                        ?: return@use FetchAttempt.Complete(
                            LinkFetchResult.Failure("页面重定向地址无效")
                        )
                    val next = try {
                        target.resolve(location).toString()
                    } catch (_: Exception) {
                        return@use FetchAttempt.Complete(
                            LinkFetchResult.Failure("页面重定向地址无效")
                        )
                    }
                    val nextUri = runCatching { URI(next) }.getOrNull()
                        ?: return@use FetchAttempt.Complete(
                            LinkFetchResult.Failure("页面重定向地址无效")
                        )
                    if (target.scheme.equals("https", true) &&
                        !nextUri.scheme.equals("https", true)
                    ) {
                        return@use FetchAttempt.Complete(
                            LinkFetchResult.Unsupported("拒绝从 HTTPS 降级到 HTTP")
                        )
                    }
                    FetchAttempt.Redirect(next)
                } else if (!response.isSuccessful) {
                    FetchAttempt.Complete(LinkFetchResult.Failure("HTTP ${response.code}"))
                } else {
                    val body = response.body
                        ?: return@use FetchAttempt.Complete(
                            LinkFetchResult.Failure("响应没有正文")
                        )
                    val mediaType = body.contentType()
                    val subtype = mediaType?.subtype?.lowercase().orEmpty()
                    val supported = mediaType == null ||
                        mediaType.type.equals("text", true) ||
                        subtype.contains("html") ||
                        subtype.contains("xhtml")
                    if (!supported) {
                        return@use FetchAttempt.Complete(
                            LinkFetchResult.Unsupported("暂不支持 ${mediaType ?: "该内容类型"}")
                        )
                    }
                    val charset = mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    val raw = readBounded(body.byteStream(), MAX_RESPONSE_BYTES, charset)
                        ?: return@use FetchAttempt.Complete(
                            LinkFetchResult.Unsupported("页面超过 512 KB")
                        )
                    val extracted = HtmlTextExtractor.extract(raw, MAX_TEXT_CHARS)
                    FetchAttempt.Complete(
                        if (extracted.text.isBlank()) {
                            LinkFetchResult.Failure("页面没有可提取正文")
                        } else {
                            LinkFetchResult.Success(extracted.title, extracted.text)
                        }
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            FetchAttempt.Complete(
                LinkFetchResult.Failure(error.message?.take(160) ?: "无法读取页面")
            )
        }
    }

    private sealed interface FetchAttempt {
        data class Redirect(val url: String) : FetchAttempt
        data class Complete(val result: LinkFetchResult) : FetchAttempt
    }

    private fun normalizeHost(value: String): String = value
        .removePrefix("[")
        .removeSuffix("]")
        .trimEnd('.')
        .lowercase()

    private fun readBounded(input: InputStream, limit: Int, charset: Charset): String? {
        val out = ByteArrayOutputStream(minOf(limit, 32 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toString(charset.name())
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_TEXT_CHARS = 6_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_LOCATION_CHARS = 4_096
    }
}
