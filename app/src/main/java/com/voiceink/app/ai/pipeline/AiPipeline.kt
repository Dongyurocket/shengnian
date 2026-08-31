package com.voiceink.app.ai.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.voiceink.app.ai.LlmGateway
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.StopReason
import com.voiceink.app.ai.adapter.LlmException
import com.voiceink.app.ai.prompt.JsonExtractor
import com.voiceink.app.ai.prompt.ParsedIntent
import com.voiceink.app.ai.prompt.Prompts
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.repo.NoteRepository
import com.voiceink.app.data.repo.TodoRepository
import com.voiceink.app.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 处理流水线（§8.1）：意图分类 + 结构化抽取合并为一次 LLM 调用。
 * 待办 → 写 todos 表 + 提醒；笔记 → 写回整理结果（关联发现在阶段 5 接入）。
 */
@Singleton
class AiPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: LlmGateway,
    private val notes: NoteRepository,
    private val todos: TodoRepository,
    private val reminder: ReminderScheduler
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Retryable : Outcome
        data object Fatal : Outcome
    }

    /** 入队：唯一工作名防重复，联网约束，指数退避（WorkManager 保证进程被杀后仍执行） */
    fun enqueue(noteId: Long) {
        val req = OneTimeWorkRequestBuilder<AiProcessWorker>()
            .setInputData(workDataOf(AiProcessWorker.KEY_NOTE_ID to noteId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("ai_process_$noteId", ExistingWorkPolicy.KEEP, req)
    }

    suspend fun process(noteId: Long): Outcome {
        val note = notes.byId(noteId) ?: return Outcome.Fatal

        val result = try {
            gateway.complete(
                LlmRequest(
                    system = Prompts.INTENT_AND_ORGANIZE,
                    user = buildString {
                        append("当前时间：").append(TimeUtils.nowString()).append('\n')
                        val cats = notes.topCategories()
                        if (cats.isNotEmpty()) {
                            append("用户常用分类（按使用频次）：")
                            append(cats.joinToString("、"))
                            append('\n')
                        }
                        if (note.intentHint == "todo") {
                            append("（用户从「新建待办」快捷入口输入，请优先判断为待办）\n")
                        }
                        append("用户输入原文：\n").append(note.content)
                    },
                    jsonSchemaName = "intent"
                )
            )
        } catch (e: LlmException) {
            notes.markFailed(noteId)
            return if (e.retriable) Outcome.Retryable else Outcome.Fatal
        } catch (e: Exception) {
            notes.markFailed(noteId)
            return Outcome.Fatal
        }

        if (result.stopReason == StopReason.MAX_TOKENS) {
            // 截断的输出大概率不是完整 JSON；标记失败（UI 可提示换长输出模型），仍尝试兜底解析
            notes.markFailed(noteId)
        }

        return when (val parsed = JsonExtractor.extractIntent(result.text)) {
            is ParsedIntent.Todo -> {
                val todoId = todos.insertFrom(parsed, sourceNoteId = noteId)
                todos.byId(todoId)?.remindAt?.let { reminder.schedule(todoId, it) }
                notes.delete(noteId)   // 原始输入迁移进 todo.content，笔记不重复留存
                Outcome.Done
            }
            is ParsedIntent.Note -> {
                notes.applyOrganization(
                    noteId,
                    parsed.copy(content = parsed.content.ifBlank { note.content })
                )
                // 关联发现 LinkDiscovery 在阶段 5 接入
                Outcome.Done
            }
            ParsedIntent.Unparseable -> {
                notes.markFailed(noteId)
                Outcome.Retryable
            }
        }
    }
}
