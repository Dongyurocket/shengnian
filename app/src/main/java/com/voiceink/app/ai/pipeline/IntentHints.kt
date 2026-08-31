package com.voiceink.app.ai.pipeline

/** 这些入口产生的内容必须保留为笔记，不能被一次 AI 误判转成待办。 */
internal fun requiresNoteIntent(intentHint: String?): Boolean =
    intentHint == "note" || intentHint == "merge"
