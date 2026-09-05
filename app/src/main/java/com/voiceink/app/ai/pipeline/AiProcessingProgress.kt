package com.voiceink.app.ai.pipeline

import androidx.work.WorkInfo
import com.voiceink.app.ai.adapter.LlmStreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 仅阶段名进入 WorkManager Data，不包含模型输出。 */
enum class AiPhase(val label: String) {
    QUEUED("等待网络或后台执行"),
    PREPARING("正在准备整理内容"),
    CONNECTING("正在连接"),
    ANALYZING("模型正在分析"),
    GENERATING("正在生成整理结果"),
    SAVING("正在保存整理结果"),
    RETRYING("连接中断或结果不完整，等待自动重试"),
    CANCELLED("整理已取消，点击重试")
}

data class AiProgress(
    val phase: AiPhase = AiPhase.QUEUED,
    val reasoningSummary: String = ""
)

/** 按 Worker ID 隔离并发任务；任务结束或取消时清除，不写入数据库或日志。 */
@Singleton
class AiSummaryStore @Inject constructor() {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    val summaries = values.asStateFlow()

    fun set(runId: String, summary: String) {
        values.update { current ->
            if (summary.isEmpty()) current - runId
            else current + (runId to summary.take(MAX_SUMMARY_CHARS))
        }
    }

    fun clear(runId: String) = values.update { it - runId }

    companion object {
        const val MAX_SUMMARY_CHARS = 600
    }
}

internal fun progressForWork(info: WorkInfo?, summary: String): AiProgress {
    val phase = when (info?.state) {
        WorkInfo.State.RUNNING -> AiPhase.entries.firstOrNull {
            it.name == info.progress.getString(AiProcessWorker.KEY_PHASE)
        } ?: AiPhase.PREPARING
        WorkInfo.State.ENQUEUED -> if (info.runAttemptCount > 0) AiPhase.RETRYING else AiPhase.QUEUED
        WorkInfo.State.CANCELLED -> AiPhase.CANCELLED
        else -> AiPhase.QUEUED
    }
    return AiProgress(phase, if (info?.state == WorkInfo.State.RUNNING) summary else "")
}

internal class AiStreamProgress(private val publish: suspend (AiProgress) -> Unit) {
    private var progress = AiProgress(AiPhase.CONNECTING)

    suspend fun onEvent(event: LlmStreamEvent) {
        val next = when (event) {
            LlmStreamEvent.Connected -> AiProgress(AiPhase.ANALYZING)
            is LlmStreamEvent.TextDelta -> progress.copy(phase = AiPhase.GENERATING)
            is LlmStreamEvent.ReasoningSummaryDelta -> progress.copy(
                reasoningSummary = (progress.reasoningSummary + event.text)
                    .take(AiSummaryStore.MAX_SUMMARY_CHARS)
            )
        }
        if (next != progress) {
            progress = next
            publish(progress)
        }
    }
}
