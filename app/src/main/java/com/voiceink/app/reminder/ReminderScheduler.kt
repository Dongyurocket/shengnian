package com.voiceink.app.reminder

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 阶段 3 实现 AlarmManager 封装（§10）；此处先提供空调用，保证流水线可编译。 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(todoId: Long, triggerAt: Long) {
        // TODO(阶段 3): setExactAndAllowWhileIdle / setWindow 降级
    }

    fun cancel(todoId: Long) {
        // TODO(阶段 3)
    }
}
