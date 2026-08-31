package com.voiceink.app.ai.pipeline

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/** 已通过主机安全策略的地址集合；请求时用它固定 DNS 结果，降低 rebinding 风险。 */
data class AllowedUrlHost(
    val host: String,
    val addresses: List<InetAddress>
)

/**
 * URL 抓取的目标地址策略。网页来源不需要访问本机或内网地址，默认拒绝这些目标，
 * 也拒绝带凭据的 URL，避免把用户信息或服务端重定向意外带入请求。
 */
object LinkUrlPolicy {
    /** 返回阻断原因；返回 null 表示 URI 通过策略检查。 */
    fun blockReason(uri: URI): String? = inspect(uri).reason

    /** 返回已解析且通过检查的地址；每次重定向都必须重新调用。 */
    fun resolve(uri: URI): AllowedUrlHost? = inspect(uri).resolution

    private fun inspect(uri: URI): Inspection {
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return Inspection("仅支持 HTTP(S) 链接")
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return Inspection("不支持包含登录凭据的链接")
        }

        val host = uri.host?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.trim()
        if (host.isNullOrBlank()) return Inspection("链接缺少有效主机名")
        val normalizedHost = host.lowercase(Locale.US)
        if (normalizedHost == "localhost" ||
            normalizedHost.endsWith(".localhost") ||
            normalizedHost.endsWith(".local")
        ) {
            return Inspection("为保护本机安全，不抓取本地地址")
        }

        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (_: Exception) {
            return Inspection("无法解析链接主机")
        }
        if (addresses.isEmpty() || addresses.any(::isRestrictedAddress)) {
            return Inspection("为保护本机安全，不抓取内网或保留地址")
        }
        return Inspection(resolution = AllowedUrlHost(normalizedHost, addresses))
    }

    private data class Inspection(
        val reason: String? = null,
        val resolution: AllowedUrlHost? = null
    )

    private fun isRestrictedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true

        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            return first == 0 ||
                (first == 100 && second in 64..127) || // RFC 6598 shared address space
                (first == 192 && second == 0 && third == 0) ||
                (first == 192 && second == 0 && third == 2) || // TEST-NET-1
                (first == 192 && second == 88 && third == 99) || // 6to4 relay anycast
                (first == 198 && second in 18..19) || // benchmarking
                (first == 198 && second == 51 && third == 100) || // TEST-NET-2
                (first == 203 && second == 0 && third == 113) || // TEST-NET-3
                first >= 224
        }

        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first and 0xfe == 0xfc) return true // IPv6 unique-local fc00::/7
            if (first == 0xfe && second and 0xc0 == 0x80) return true // fe80::/10

            // IPv4-mapped IPv6 addresses can otherwise bypass the IPv4 checks.
            val mapped = bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
            if (mapped) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isRestrictedAddress(v4)
            }
        }
        return false
    }
}
