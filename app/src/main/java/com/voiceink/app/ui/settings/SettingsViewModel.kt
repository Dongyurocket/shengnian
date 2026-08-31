package com.voiceink.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.voiceink.app.ai.EmbeddingEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.embedding.EmbeddingClient
import com.voiceink.app.ai.pipeline.LinkScanWorker
import com.voiceink.app.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val embeddingClient: EmbeddingClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
        val baseUrl: String = "",
        val model: String = "",
        val apiKey: String = "",
        val embedEnabled: Boolean = false,
        val embedBaseUrl: String = "",
        val embedModel: String = "",
        val embedApiKey: String = "",
        val embedTestResult: String? = null,
        val linkEnabled: Boolean = true,
        val rebuilding: Boolean = false,
        val openDirectCapture: Boolean = false,
        val remindLead: String = "5",
        val saved: Boolean = false
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
        _ui.update { block(it).copy(saved = false, embedTestResult = null) }

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
