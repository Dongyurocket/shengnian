package com.voiceink.app.update

import com.voiceink.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val version: String,      // 去掉 v 前缀，如 0.2.0
    val notes: String,
    val apkUrl: String?,      // release 附件中的 .apk 直链
    val pageUrl: String       // release 页面（无附件时回退用浏览器打开）
)

/** 通过 GitHub Releases API 检查更新 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    companion object {
        const val REPO = "Dongyurocket/shengnian"
        const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    }

    /** 有更新返回 UpdateInfo；已是最新返回 null；网络/解析失败抛异常 */
    suspend fun check(): UpdateInfo? =
        checkAt(LATEST_API, BuildConfig.VERSION_NAME)

    /** 可注入 URL 的内部入口，便于契约测试，不改变线上固定仓库 */
    internal suspend fun checkAt(url: String, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Shengnian/$currentVersion")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("检查更新失败（HTTP ${resp.code}）")
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val tag = stringField(root, "tag_name") ?: error("响应缺少 tag_name")
                val latest = tag.trim().removePrefix("v").trim()
                if (!isNewer(latest, currentVersion)) return@withContext null
                val notes = stringField(root, "body").orEmpty()
                val pageUrl = stringField(root, "html_url")
                    ?: "https://github.com/$REPO/releases/latest"
                val apkUrl = (root["assets"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.firstOrNull {
                        stringField(it, "name")?.endsWith(".apk", ignoreCase = true) == true
                    }
                    ?.let { stringField(it, "browser_download_url") }
                UpdateInfo(latest, notes, apkUrl, pageUrl)
            }
        }

    private fun stringField(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    /** 语义化版本比较：latest > current 才返回 true */
    internal fun isNewer(latest: String, current: String): Boolean {
        val a = versionParts(latest) ?: return false
        val b = versionParts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(value: String): List<Int>? {
        val core = value.substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.any { it.isBlank() || it.toIntOrNull() == null }) return null
        return parts.map { it.toInt() }
    }
}
