package com.voiceink.app.reminder

/** 待办提醒的通知方式：均为抬头弹窗通知，仅声音/振动行为不同。 */
enum class ReminderMode(val label: String) {
    SOUND("响铃"),
    VIBRATE("振动"),
    SILENT("静音");

    companion object {
        fun fromName(name: String?): ReminderMode =
            entries.firstOrNull { it.name == name } ?: SOUND
    }
}
