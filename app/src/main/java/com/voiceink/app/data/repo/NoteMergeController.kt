package com.voiceink.app.data.repo

import com.voiceink.app.capture.CaptureController
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteStatus
import javax.inject.Inject
import javax.inject.Singleton

/** 构造 AI 合并输入的纯函数，便于测试和后续替换为专用 merge prompt。 */
internal fun buildMergePrompt(notes: List<NoteEntity>): String = buildString {
    append("请将下面选中的多条已有笔记合并整理为一条新的笔记。")
    append("去除重复，整合互补信息，保留重要细节和不确定性，不要凭空编造。\n\n")
    notes.forEachIndexed { index, note ->
        append("--- 来源笔记 ").append(index + 1).append(" ---\n")
        append("标题：").append(note.title.ifBlank { "未命名" }).append('\n')
        append("正文：\n").append(note.content.take(NoteMergeController.MAX_NOTE_CHARS)).append("\n\n")
    }
}.toString().take(NoteMergeController.MAX_TOTAL_CHARS)

@Singleton
class NoteMergeController @Inject constructor(
    private val notes: NoteRepository,
    private val capture: CaptureController
) {
    suspend fun merge(ids: List<Long>): Long {
        val uniqueIds = ids.distinct()
        require(uniqueIds.size >= 2) { "至少选择两条笔记" }
        val selected = uniqueIds.mapNotNull { notes.byId(it) }
            .filter { it.status == NoteStatus.READY }
        require(selected.size >= 2) { "只能合并已整理完成的笔记" }
        return capture.capture(
            text = buildMergePrompt(selected),
            source = "merge",
            intentHint = "merge"
        )
    }

    companion object {
        const val MAX_NOTE_CHARS = 6_000
        const val MAX_TOTAL_CHARS = 20_000
    }
}
