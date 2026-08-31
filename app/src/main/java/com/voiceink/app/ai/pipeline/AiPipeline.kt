package com.voiceink.app.ai.pipeline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 处理流水线编排（§8.1）。
 * 阶段 1 仅有入队占位：文本已落库 PENDING_AI，阶段 2 接入 WorkManager + LLM 处理。
 */
@Singleton
class AiPipeline @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Retryable : Outcome
        data object Fatal : Outcome
    }

    /** 入队一条笔记的 AI 处理。阶段 2 将改为 WorkManager 唯一任务 + 联网约束。 */
    fun enqueue(noteId: Long) {
        // TODO(阶段 2): WorkManager.enqueueUniqueWork("ai_process_$noteId", ...)
    }
}
