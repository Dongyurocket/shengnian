package com.voiceink.app.ai.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voiceink.app.data.local.dao.EmbeddingDao
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.repo.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 每日增量关联扫描（§9.3）：只对 lastFullScan 之后创建的 READY 笔记跑关联发现。
 * rebuild=true 时清空 note_links / note_embeddings 全量重建（设置页「重建知识网络」）。
 */
@HiltWorker
class LinkScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notes: NoteDao,
    private val links: LinkDao,
    private val embeddings: EmbeddingDao,
    private val linkDiscovery: LinkDiscovery,
    private val settings: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.isLinkDiscoveryEnabled()) return Result.success()
        val rebuild = inputData.getBoolean(KEY_REBUILD, false)
        val since = if (rebuild) {
            links.clear()
            embeddings.clear()
            0L
        } else {
            settings.lastLinkScan()
        }
        return try {
            notes.readyIdsSince(since).forEach { linkDiscovery.discoverFor(it) }
            settings.setLastLinkScan(System.currentTimeMillis())
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_REBUILD = "rebuild"
        const val UNIQUE_DAILY = "link_scan_daily"
        const val UNIQUE_REBUILD = "link_scan_rebuild"
    }
}
