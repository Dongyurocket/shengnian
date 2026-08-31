package com.voiceink.app.ai.prompt

/** 意图分流结果（§8.1） */
sealed interface ParsedIntent {
    data class Note(
        val title: String,
        val content: String,
        val category: String?,
        val type: String?,
        val mood: String?,
        val tags: List<String>,
        val summary: String?
    ) : ParsedIntent

    data class Todo(
        val content: String,
        val priority: Int,
        val deadline: Long?,              // epoch millis，已从 yyyy-MM-dd HH:mm 换算
        val remindLeadMinutes: Int?
    ) : ParsedIntent

    data object Unparseable : ParsedIntent
}
