package com.voiceink.app.ai.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager 入口：保证进程被杀后仍执行，网络断开自动重试（§8.1） */
@HiltWorker
class AiProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: AiPipeline
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        if (noteId < 0) return Result.failure()
        return when (pipeline.process(noteId)) {
            is AiPipeline.Outcome.Done -> Result.success()
            is AiPipeline.Outcome.Retryable ->
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            is AiPipeline.Outcome.Fatal -> Result.failure()  // 如 401：重试无意义，等用户改配置
        }
    }

    companion object {
        const val KEY_NOTE_ID = "noteId"
    }
}
