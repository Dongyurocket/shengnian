package com.voiceink.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 下载完成 → 拉起系统安装器（unknown-apps 授权由系统引导） */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val dm = context.getSystemService(DownloadManager::class.java)
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return
        try {
            if (!cursor.moveToFirst()) return
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0 ||
                cursor.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL
            ) return
            val mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            if (mimeIndex >= 0 &&
                cursor.getString(mimeIndex) != "application/vnd.android.package-archive"
            ) return
        } finally {
            cursor.close()
        }

        val uri = dm.getUriForDownloadedFile(id) ?: return
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(install) }
            .onFailure {
                // 没有可用安装器或系统阻止后台拉起时，保留 DownloadManager 通知供用户点击。
            }
    }
}
