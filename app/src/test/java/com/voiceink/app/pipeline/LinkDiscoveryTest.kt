package com.voiceink.app.ai.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 关联发现算法单测（§9.3 阈值） */
class LinkDiscoveryTest {

    @Test
    fun `余弦相似度 - 相同向量为 1，正交为 0`() {
        val a = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, cosine(a, a), 1e-5f)
        assertEquals(0f, cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-5f)
        assertEquals(0f, cosine(floatArrayOf(0f, 0f), a), 1e-5f) // 零向量安全
    }

    @Test
    fun `余弦相似度 - 维度不一致时按较短计算`() {
        val s = cosine(floatArrayOf(1f, 0f, 99f), floatArrayOf(1f, 0f))
        assertEquals(1f, s, 1e-5f)
    }

    @Test
    fun `Jaccard - 共享 2 个标签各 4 个标签时为阈值 0_33`() {
        // A=4 个标签，B=4 个标签，共享 2 个 → 2/(4+4-2) = 0.333
        val j = jaccard(shared = 2, aTotal = 4, bTotal = 4)
        assertTrue(j < JACCARD_RECALL_THRESHOLD) // 低于 0.34 不召回
        assertTrue(jaccard(2, 3, 3) >= JACCARD_RECALL_THRESHOLD) // 2/4=0.5 召回
    }

    @Test
    fun `综合分 - LLM 确认加权`() {
        assertEquals(0.6f * 0.8f + 0.4f, combineScore(0.8f, true), 1e-5f)
        assertEquals(0.8f, combineScore(0.8f, false), 1e-5f)
        // LLM 确认但召回分低 → 低于落库线 0.55
        assertTrue(combineScore(0.2f, true) < LINK_MIN_SCORE)
        // 高召回 + LLM 确认 → 过线
        assertTrue(combineScore(0.75f, true) >= LINK_MIN_SCORE)
    }

    @Test
    fun `向量序列化往返`() {
        val v = floatArrayOf(0.5f, -1.25f, 3.14f, 0f)
        val bytes = v.toBytes()
        val back = bytes.toFloats()
        assertTrue(v.contentEquals(back))
    }
}
