package com.voiceink.app.capture

import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.data.repo.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureController @Inject constructor(
    private val notes: NoteRepository,
    private val pipeline: AiPipeline
) {
    /**
     * 保存一条输入：先落库（status=PENDING_AI）再异步 AI，全程不阻塞 UI。
     * @param intentHint 入口预设意图（如桌面快捷方式"新建待办"），AI 阶段参考
     */
    suspend fun capture(text: String, source: String = "app", intentHint: String? = null): Long {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "内容为空" }
        val noteId = notes.insertRaw(trimmed, source, intentHint)
        pipeline.enqueue(noteId)
        return noteId
    }
}
