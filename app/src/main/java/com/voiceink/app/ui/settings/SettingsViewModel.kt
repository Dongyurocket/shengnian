package com.voiceink.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    data class UiState(
        val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
        val baseUrl: String = "",
        val model: String = "",
        val apiKey: String = "",
        val openDirectCapture: Boolean = false,
        val remindLead: String = "5",
        val saved: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.llmConfig.first()
            _ui.value = UiState(
                protocol = cfg.protocol,
                baseUrl = cfg.baseUrl,
                model = cfg.model,
                apiKey = repo.savedLlmApiKey(),
                openDirectCapture = repo.openDirectCapture.first(),
                remindLead = repo.remindLeadMinutes.first().toString()
            )
        }
    }

    fun update(block: (UiState) -> UiState) = _ui.update { block(it).copy(saved = false) }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            repo.saveLlm(s.protocol, s.baseUrl, s.model, s.apiKey)
            repo.setOpenDirectCapture(s.openDirectCapture)
            s.remindLead.toIntOrNull()?.let { repo.setRemindLeadMinutes(it) }
            _ui.update { it.copy(saved = true) }
        }
    }
}
