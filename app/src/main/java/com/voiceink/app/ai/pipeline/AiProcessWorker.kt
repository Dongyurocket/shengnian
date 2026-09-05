package com.voiceink.app.ai.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager 入口：保证进程被杀后仍执行，网络断开自动重试（§8.1） */
@HiltWorker
class AiProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: AiPipeline,
    private val summaries: AiSummaryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        val forceNote = inputData.getBoolean(KEY_FORCE_NOTE, false)
        if (noteId < 0) return Result.failure()
        var lastPhase: AiPhase? = null
        suspend fun publish(progress: AiProgress) {
            summaries.set(id.toString(), progress.reasoningSummary)
            if (lastPhase != progress.phase) {
                setProgress(workDataOf(KEY_PHASE to progress.phase.name))
                lastPhase = progress.phase
            }
        }
        return try {
            when (pipeline.process(noteId, forceNote, finalAttempt = runAttemptCount >= 5, onProgress = ::publish)) {
                is AiPipeline.Outcome.Done -> Result.success()
                is AiPipeline.Outcome.Retryable -> {
                    publish(AiProgress(AiPhase.RETRYING))
                    Result.retry()
                }
                is AiPipeline.Outcome.Fatal -> Result.failure()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 5) {
                publish(AiProgress(AiPhase.RETRYING))
                Result.retry()
            } else {
                pipeline.markFailedIfPending(noteId)
                Result.failure()
            }
        } finally {
            summaries.clear(id.toString())
        }
    }

    companion object {
        const val KEY_NOTE_ID = "noteId"
        const val KEY_FORCE_NOTE = "forceNote"
        const val KEY_PHASE = "phase"
    }
}
