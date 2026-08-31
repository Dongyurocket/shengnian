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
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteStatus
import com.voiceink.app.data.repo.NoteAttachmentRepository
import com.voiceink.app.data.repo.NoteRepository
import com.voiceink.app.data.repo.NoteSourceRepository
import com.voiceink.app.data.repo.TodoRepository
import com.voiceink.app.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
    private val attachments: NoteAttachmentRepository,
    private val sources: NoteSourceRepository,
    private val todos: TodoRepository,
    private val linkDiscovery: LinkDiscovery,
    private val reminder: ReminderScheduler
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Retryable : Outcome
        data object Fatal : Outcome
    }

    /** 入队：唯一工作名防重复，联网约束，指数退避（WorkManager 保证进程被杀后仍执行） */
    fun enqueue(noteId: Long, forceNote: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<AiProcessWorker>()
            .setInputData(
                workDataOf(
                    AiProcessWorker.KEY_NOTE_ID to noteId,
                    AiProcessWorker.KEY_FORCE_NOTE to forceNote
                )
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "ai_process_$noteId",
                if (forceNote) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                req
            )
    }

    suspend fun process(noteId: Long, forceNote: Boolean = false): Outcome {
        val note = notes.byId(noteId) ?: return Outcome.Fatal
        if (note.status != NoteStatus.PENDING_AI && note.status != NoteStatus.AI_FAILED) {
            return Outcome.Done
        }
        val rawContent = note.rawContent.ifBlank { note.content }
        val sourceRows = try {
            sources.refreshForNote(noteId, rawContent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val sourceContext = sources.buildPromptContext(sourceRows)
        val imagePayloads = try {
            attachments.imagesForLlm(noteId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val attachmentCount = try {
            attachments.attachmentCount(noteId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            0
        }
        if (rawContent == NoteAttachmentRepository.IMAGE_ONLY_PLACEHOLDER &&
            attachmentCount > 0 && imagePayloads.isEmpty()
        ) {
            if (notes.markFailedIfCurrent(note)) return Outcome.Fatal
            return Outcome.Done
        }
        val keepAsNote = forceNote || requiresNoteIntent(note.intentHint)
        if (!isCurrent(note, attachmentCount)) return Outcome.Done

        val result = try {
            gateway.complete(
                LlmRequest(
                    system = buildString {
                        append(Prompts.INTENT_AND_ORGANIZE)
                        if (note.intentHint == "merge") {
                            append("\n此次输入来自多条已有笔记的合并任务：请去重、整合互补信息，保留重要细节，输出一条完整笔记。")
                        }
                        if (keepAsNote) {
                            append("\n此次是对已有笔记的重新整理，必须输出 intent=note，不要把整条笔记转换成 todo。")
                        }
                        inspirationHint(note.intentHint)?.let { append("\n$it") }
                    },
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
                        append("用户输入原文：\n").append(rawContent)
                        if (attachmentCount > imagePayloads.size) {
                            append("\n有 ").append(attachmentCount - imagePayloads.size)
                                .append(" 张本地图片无法读取，请不要猜测其内容。")
                        }
                        append(sourceContext)
                    },
                    jsonSchemaName = "intent",
                    images = imagePayloads
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            if (!notes.markFailedIfCurrent(note)) return Outcome.Done
            return if (e.retriable) Outcome.Retryable else Outcome.Fatal
        } catch (e: Exception) {
            if (!notes.markFailedIfCurrent(note)) return Outcome.Done
            return Outcome.Fatal
        }

        if (result.stopReason == StopReason.MAX_TOKENS) {
            // 截断的输出大概率不是完整 JSON；交给 WorkManager 重试，避免写入半截结果。
            if (notes.markFailedIfCurrent(note)) return Outcome.Retryable
            return Outcome.Done
        }

        if (!isCurrent(note, attachmentCount)) return Outcome.Done

        return when (val parsed = JsonExtractor.extractIntent(result.text)) {
            is ParsedIntent.Todo -> {
                if (keepAsNote) {
                    // 编辑/合并场景保留笔记，不因模型一次误判而删除用户内容。
                    val fallback = ParsedIntent.Note(
                        title = note.title.ifBlank { parsed.content.take(24) },
                        content = rawContent,
                        category = note.category,
                        type = note.type,
                        mood = note.mood,
                        tags = emptyList(),
                        summary = note.summary,
                        todos = listOf(parsed.content),
                        isInspiration = note.isInspiration
                    )
                    if (!notes.applyOrganization(noteId, fallback, expected = note)) return Outcome.Done
                    clearOpenExtractedTodos(noteId)
                    val todoId = todos.insertFrom(parsed, sourceNoteId = noteId)
                    scheduleReminderIfPossible(todoId)
                    try {
                        linkDiscovery.discoverFor(noteId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // 关联失败不阻断已完成的笔记整理。
                    }
                    Outcome.Done
                } else {
                    // 先创建可回滚的独立待办，再用快照条件删除笔记；用户并发编辑时不删除新版本。
                    val attachmentRows = attachments.listForNote(noteId)
                    val todoId = todos.findOpenForNoteByContent(noteId, parsed.content)?.id
                        ?: todos.insertFrom(parsed, sourceNoteId = noteId)
                    if (!notes.deleteIfCurrentAndDetachTodo(note, todoId)) {
                        todos.delete(todoId)
                        return Outcome.Done
                    }
                    // 原始输入迁移进 todo.content，笔记不保留；临时 sourceNoteId 已在事务中解绑。
                    clearExtractedTodos(noteId)
                    scheduleReminderIfPossible(todoId)
                    deleteAttachmentFilesIfPossible(attachmentRows)
                    Outcome.Done
                }
            }
            is ParsedIntent.Note -> {
                val organized = parsed.copy(
                    content = parsed.content.ifBlank { rawContent },
                    isInspiration = when (note.intentHint) {
                        "note" -> true
                        "note_plain" -> false
                        else -> parsed.isInspiration
                    }
                )
                if (!notes.applyOrganization(noteId, organized, expected = note)) return Outcome.Done
                // 笔记中提炼出的待办：保留笔记，待办以 sourceNoteId 回溯（§11.3）
                clearOpenExtractedTodos(noteId)
                parsed.todos.forEach { content ->
                    todos.insertFrom(
                        ParsedIntent.Todo(content, priority = 1, deadline = null, remindLeadMinutes = null),
                        sourceNoteId = noteId
                    )
                }
                // 步骤 2：关联发现（§9，内部检查开关，失败不影响主流程）
                try {
                    linkDiscovery.discoverFor(noteId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 关联失败不阻断已完成的笔记整理。
                }
                Outcome.Done
            }
            ParsedIntent.Unparseable -> {
                if (notes.markFailedIfCurrent(note)) Outcome.Retryable else Outcome.Done
            }
        }
    }

    suspend fun markFailedIfPending(noteId: Long) {
        notes.byId(noteId)?.let { note -> notes.markFailedIfCurrent(note) }
    }

    private suspend fun clearOpenExtractedTodos(noteId: Long) {
        todos.listForNote(noteId)
            .filter { !it.done }
            .forEach { todo -> cancelReminderIfPossible(todo.id) }
        todos.clearOpenForNote(noteId)
    }

    private suspend fun clearExtractedTodos(noteId: Long) {
        todos.listForNote(noteId).forEach { todo -> cancelReminderIfPossible(todo.id) }
        todos.deleteForNote(noteId)
    }

    private suspend fun deleteAttachmentFilesIfPossible(attachmentsToDelete: List<com.voiceink.app.data.local.entity.NoteAttachmentEntity>) {
        try {
            attachments.deleteFiles(attachmentsToDelete)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 笔记和待办已经完成，文件清理失败不应让结果回到待处理状态。
        }
    }

    private suspend fun scheduleReminderIfPossible(todoId: Long) {
        val todo = todos.byId(todoId) ?: return
        val reminders = todos.listReminders(todoId)
        if (reminders.isEmpty()) {
            todo.remindAt?.takeIf { it > System.currentTimeMillis() }?.let {
                try {
                    reminder.schedule(todo.id, 0, it)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 待办已经落库；开机重排或用户后续编辑仍可恢复提醒。
                }
            }
            return
        }
        try {
            reminders.filter { it.triggerAt > System.currentTimeMillis() }.forEach { item ->
                reminder.schedule(todo.id, item.sequence, item.triggerAt)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 待办已经落库；开机重排或用户后续编辑仍可恢复提醒。
        }
    }

    private suspend fun cancelReminderIfPossible(todoId: Long) {
        try {
            val reminders = todos.listReminders(todoId)
            if (reminders.isEmpty()) {
                reminder.cancel(todoId)
            } else {
                reminders.forEach { item -> reminder.cancel(todoId, item.sequence) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 清理数据库中的待办不应因系统闹钟状态异常而中断。
        }
    }

    private suspend fun isCurrent(note: NoteEntity, attachmentCount: Int): Boolean {
        val current = notes.byId(note.id) ?: return false
        if ((current.status != com.voiceink.app.data.local.entity.NoteStatus.PENDING_AI &&
                current.status != com.voiceink.app.data.local.entity.NoteStatus.AI_FAILED) ||
            current.updatedAt != note.updatedAt ||
            current.content != note.content ||
            current.rawContent != note.rawContent ||
            current.intentHint != note.intentHint
        ) return false
        return attachments.attachmentCount(note.id) == attachmentCount
    }

}
