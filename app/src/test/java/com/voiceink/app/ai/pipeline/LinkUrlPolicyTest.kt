package com.voiceink.app.ai.pipeline

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkUrlPolicyTest {
    @Test
    fun `拒绝本机和私网地址`() {
        assertEquals(
            "为保护本机安全，不抓取内网或保留地址",
            LinkUrlPolicy.blockReason(URI("http://127.0.0.1:8080/"))
        )
        assertEquals(
            "为保护本机安全，不抓取内网或保留地址",
            LinkUrlPolicy.blockReason(URI("http://192.168.1.20/"))
        )
        assertEquals(
            "为保护本机安全，不抓取内网或保留地址",
            LinkUrlPolicy.blockReason(URI("http://[::1]/"))
        )
        assertEquals(
            "为保护本机安全，不抓取内网或保留地址",
            LinkUrlPolicy.blockReason(URI("http://169.254.169.254/latest/meta-data/"))
        )
        assertEquals(
            "为保护本机安全，不抓取内网或保留地址",
            LinkUrlPolicy.blockReason(URI("http://2130706433/"))
        )
    }

    @Test
    fun `拒绝凭据和非网页协议`() {
        assertEquals(
            "不支持包含登录凭据的链接",
            LinkUrlPolicy.blockReason(URI("https://user:pass@8.8.8.8/"))
        )
        assertEquals(
            "仅支持 HTTP(S) 链接",
            LinkUrlPolicy.blockReason(URI("ftp://8.8.8.8/file"))
        )
    }

    @Test
    fun `允许公网 IP 地址`() {
        assertNull(LinkUrlPolicy.blockReason(URI("https://8.8.8.8/")))
    }
}
