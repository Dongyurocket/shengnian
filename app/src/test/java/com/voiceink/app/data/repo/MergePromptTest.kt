package com.voiceink.app.data.repo

import com.voiceink.app.data.local.entity.NoteEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePromptTest {
    @Test
    fun `合并输入保留来源标题和正文并包含去重要求`() {
        val prompt = buildMergePrompt(
            listOf(
                NoteEntity(id = 1, title = "第一条", content = "关于链接整理"),
                NoteEntity(id = 2, title = "第二条", content = "关于图片识别")
            )
        )
        assertTrue(prompt.contains("来源笔记 1"))
        assertTrue(prompt.contains("第一条"))
        assertTrue(prompt.contains("关于图片识别"))
        assertTrue(prompt.contains("去除重复"))
    }

    @Test
    fun `合并输入按传入顺序编号`() {
        val prompt = buildMergePrompt(
            listOf(
                NoteEntity(id = 2, title = "后选", content = "B"),
                NoteEntity(id = 1, title = "先选", content = "A")
            )
        )
        assertTrue(prompt.indexOf("后选") < prompt.indexOf("先选"))
    }


    @Test
    fun `超长正文被截断且原始笔记不会出现在删除指令中`() {
        val prompt = buildMergePrompt(
            listOf(NoteEntity(id = 1, title = "长文", content = "x".repeat(30_000)))
        )
        assertTrue(prompt.length <= NoteMergeController.MAX_TOTAL_CHARS)
        assertFalse(prompt.contains("删除原笔记"))
    }
}
