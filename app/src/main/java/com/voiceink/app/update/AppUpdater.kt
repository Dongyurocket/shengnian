package com.voiceink.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 下载新版 APK 到应用专属下载目录；完成后由 DownloadCompleteReceiver 拉起安装 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun download(apkUrl: String, version: String) {
        val dm = context.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("声念 v$version")
            .setDescription("正在下载更新包…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, "shengnian-v$version.apk"
            )
        dm.enqueue(req)
    }
}
