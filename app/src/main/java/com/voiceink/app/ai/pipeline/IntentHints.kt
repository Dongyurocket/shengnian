package com.voiceink.app.ai.pipeline

/** 这些入口产生的内容必须保留为笔记，不能被一次 AI 误判转成待办。 */
internal fun requiresNoteIntent(intentHint: String?): Boolean =
    intentHint == "note" || intentHint == "note_plain" || intentHint == "merge"

/** 用户显式标记的灵感意图提示，供 AiPipeline 拼入 Prompt。 */
internal fun inspirationHint(intentHint: String?): String? = when (intentHint) {
    "note" -> "用户明确标记为灵感，请务必输出 is_inspiration=true。"
    "note_plain" -> "用户明确标记为普通记录，请务必输出 is_inspiration=false。"
    else -> null
}
