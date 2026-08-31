package com.voiceink.app.capture

import android.net.Uri
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.ai.pipeline.requiresNoteIntent
import com.voiceink.app.data.repo.NoteAttachmentRepository
import com.voiceink.app.data.repo.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureController @Inject constructor(
    private val notes: NoteRepository,
    private val attachments: NoteAttachmentRepository,
    private val pipeline: AiPipeline
) {
    /**
     * 保存一条输入：文字/图片先落库，再异步 AI。图片复制到私有目录后才入队，
     * 因此 WorkManager 不依赖短暂的 Photo Picker 授权。
     */
    suspend fun capture(
        text: String,
        source: String = "app",
        intentHint: String? = null,
        imageUris: List<Uri> = emptyList()
    ): Long {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || imageUris.isNotEmpty()) { "内容为空" }
        val noteId = notes.insertRaw(
            trimmed.ifBlank { NoteAttachmentRepository.IMAGE_ONLY_PLACEHOLDER },
            source,
            intentHint
        )
        try {
            attachments.copyAllFromUris(noteId, imageUris)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 笔记已经落库；即使附件复制阶段异常，也继续让 AI 处理文字或可用图片。
        }
        pipeline.enqueue(
            noteId,
            forceNote = requiresNoteIntent(intentHint)
        )
        return noteId
    }
}