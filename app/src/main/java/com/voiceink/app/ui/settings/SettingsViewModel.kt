package com.voiceink.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.voiceink.app.ai.EmbeddingEndpoint
import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmGateway
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.embedding.EmbeddingClient
import com.voiceink.app.ai.pipeline.LinkScanWorker
import com.voiceink.app.data.export.MarkdownExporter
import com.voiceink.app.data.repo.SettingsRepository
import com.voiceink.app.update.AppUpdater
import com.voiceink.app.update.UpdateChecker
import com.voiceink.app.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val checking: Boolean = false,
    val message: String? = null,
    val available: UpdateInfo? = null,
    val downloadQueued: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val embeddingClient: EmbeddingClient,
    private val gateway: LlmGateway,
    private val exporter: MarkdownExporter,
    private val updateChecker: UpdateChecker,
    private val appUpdater: AppUpdater,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
        val baseUrl: String = "",
        val model: String = "",
        val apiKey: String = "",
        val llmTestResult: String? = null,
        val embedEnabled: Boolean = false,
        val embedBaseUrl: String = "",
        val embedModel: String = "",
        val embedApiKey: String = "",
        val embedTestResult: String? = null,
        val linkEnabled: Boolean = true,
        val rebuilding: Boolean = false,
        val exportResult: String? = null,
        val openDirectCapture: Boolean = false,
        val remindLead: String = "5",
        val saved: Boolean = false,
        val update: UpdateUiState = UpdateUiState()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.llmConfig.first()
            val embed = repo.embeddingEndpoint()
            _ui.value = UiState(
                protocol = cfg.protocol,
                baseUrl = cfg.baseUrl,
                model = cfg.model,
                apiKey = repo.savedLlmApiKey(),
                embedEnabled = embed.enabled,
                embedBaseUrl = embed.baseUrl,
                embedModel = embed.model,
                embedApiKey = embed.apiKey,
                linkEnabled = repo.linkDiscoveryEnabled.first(),
                openDirectCapture = repo.openDirectCapture.first(),
                remindLead = repo.remindLeadMinutes.first().toString()
            )
        }
    }

    fun update(block: (UiState) -> UiState) =
        _ui.update { block(it).copy(saved = false, embedTestResult = null, llmTestResult = null) }

    /** LLM 测试连接（§6.3）：用表单当前值直接测，不要求先保存 */
    fun testLlm() {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(llmTestResult = "测试中…") }
            val result = gateway.testEndpoint(LlmEndpoint(s.baseUrl, s.apiKey, s.model, s.protocol))
            _ui.update { it.copy(llmTestResult = result) }
        }
    }

    /** 导出备份到用户选择的目录（§6.4） */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(exportResult = "导出中…") }
            try {
                val result = exporter.exportAll(uri)
                _ui.update {
                    it.copy(
                        exportResult = buildString {
                            append("已导出 ").append(result.noteCount).append(" 条笔记")
                            if (result.attachmentFailures > 0) {
                                append("，").append(result.attachmentFailures).append(" 个附件失败")
                            } else {
                                append("（含附件与 JSON）")
                            }
                        }
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(exportResult = "导出失败：${error.message?.take(60).orEmpty()}") }
            }
        }
    }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            repo.saveLlm(s.protocol, s.baseUrl, s.model, s.apiKey)
            repo.saveEmbedding(s.embedEnabled, s.embedBaseUrl, s.embedModel, s.embedApiKey)
            repo.setLinkDiscoveryEnabled(s.linkEnabled)
            repo.setOpenDirectCapture(s.openDirectCapture)
            s.remindLead.toIntOrNull()?.let { repo.setRemindLeadMinutes(it) }
            _ui.update { it.copy(saved = true) }
        }
    }

    /** 查询 GitHub 最新稳定版；有更新时由设置页弹出更新日志 */
    fun checkForUpdates() {
        if (_ui.value.update.checking) return
        viewModelScope.launch {
            _ui.update {
                it.copy(update = UpdateUiState(checking = true))
            }
            try {
                val info = updateChecker.check()
                _ui.update {
                    it.copy(
                        update = if (info == null) {
                            UpdateUiState(message = "已是最新版本")
                        } else {
                            UpdateUiState(
                                message = "发现 v${info.version}",
                                available = info
                            )
                        }
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        update = UpdateUiState(
                            message = "检查失败：${error.message?.take(60).orEmpty()}"
                        )
                    )
                }
            }
        }
    }

    fun dismissUpdate() {
        _ui.update { it.copy(update = it.update.copy(available = null)) }
    }

    /** 优先下载 release APK；没有附件时打开发布页 */
    fun downloadUpdate(info: UpdateInfo) {
        val apkUrl = info.apkUrl
        if (apkUrl.isNullOrBlank()) {
            openReleasePage(info)
            return
        }
        viewModelScope.launch {
            _ui.update {
                it.copy(update = UpdateUiState(message = "正在准备下载 v${info.version}…"))
            }
            try {
                appUpdater.download(apkUrl, info.version)
                _ui.update {
                    it.copy(
                        update = UpdateUiState(
                            message = "已开始下载 v${info.version}，完成后点击通知安装",
                            downloadQueued = true
                        )
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        update = UpdateUiState(
                            message = "下载失败：${error.message?.take(60).orEmpty()}",
                            available = info
                        )
                    )
                }
            }
        }
    }

    fun openReleasePage(info: UpdateInfo) {
        _ui.update {
            it.copy(update = UpdateUiState(message = "已打开 v${info.version} 发布页"))
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )
        }.onFailure { error ->
            _ui.update {
                it.copy(update = UpdateUiState(message = "无法打开发布页：${error.message?.take(50).orEmpty()}"))
            }
        }
    }

    /** Embedding 测试连接：成功返回向量维度（§9.4） */
    fun testEmbedding() {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(embedTestResult = "测试中…") }
            val dims = embeddingClient.test(
                EmbeddingEndpoint(true, s.embedBaseUrl, s.embedApiKey, s.embedModel)
            )
            _ui.update {
                it.copy(
                    embedTestResult = if (dims != null) "连接成功，向量维度 $dims"
                    else "连接失败，请检查 Base URL / Key / 模型名"
                )
            }
        }
    }

    /** 重建知识网络（§9.3）：清空关联与向量，后台全量重算 */
    fun rebuildNetwork() {
        viewModelScope.launch {
            WorkManager.getInstance(context).enqueueUniqueWork(
                LinkScanWorker.UNIQUE_REBUILD,
                androidx.work.ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LinkScanWorker>()
                    .setInputData(workDataOf(LinkScanWorker.KEY_REBUILD to true))
                    .build()
            )
            _ui.update { it.copy(rebuilding = true) }
        }
    }
}
