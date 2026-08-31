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
        val forceNote = inputData.getBoolean(KEY_FORCE_NOTE, false)
        if (noteId < 0) return Result.failure()
        return try {
            when (pipeline.process(noteId, forceNote)) {
                is AiPipeline.Outcome.Done -> Result.success()
                is AiPipeline.Outcome.Retryable ->
                    if (runAttemptCount < 5) Result.retry() else Result.failure()
                is AiPipeline.Outcome.Fatal -> Result.failure()  // 如 401：重试无意义，等用户改配置
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                pipeline.markFailedIfPending(noteId)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_NOTE_ID = "noteId"
        const val KEY_FORCE_NOTE = "forceNote"
    }
}
