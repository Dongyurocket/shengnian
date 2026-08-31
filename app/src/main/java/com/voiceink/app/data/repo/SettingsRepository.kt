package com.voiceink.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceink.app.ai.EmbeddingEndpoint
import com.voiceink.app.ai.LlmEndpoint
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.ThinkingEffort
import com.voiceink.app.reminder.ReminderMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * 非密配置（协议/BaseUrl/Model）存 DataStore；
 * 两类 API Key（聊天 LLM、Embedding）均经 Keystore 加密（§5）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val llmKeyStore = ApiKeyStore(context, "voiceink_llm_key")
    private val embedKeyStore = ApiKeyStore(context, "voiceink_embed_key")

    // ---- 聊天 LLM ----

    data class LlmConfig(
        val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
        val baseUrl: String = "",
        val model: String = "",
        val thinkingEnabled: Boolean = false,
        val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM
    )

    private object Keys {
        val PROTOCOL = stringPreferencesKey("llm_protocol")
        val BASE_URL = stringPreferencesKey("llm_base_url")
        val MODEL = stringPreferencesKey("llm_model")
        val THINKING_ENABLED = booleanPreferencesKey("llm_thinking_enabled")
        val THINKING_EFFORT = stringPreferencesKey("llm_thinking_effort")
        val EMBED_ENABLED = booleanPreferencesKey("embed_enabled")
        val EMBED_BASE_URL = stringPreferencesKey("embed_base_url")
        val EMBED_MODEL = stringPreferencesKey("embed_model")
        val DIRECT_CAPTURE = booleanPreferencesKey("open_direct_capture")
        val REMIND_LEAD = stringPreferencesKey("remind_lead_minutes")
        val REMINDER_MODE = stringPreferencesKey("reminder_mode")
        val LINK_ENABLED = booleanPreferencesKey("link_discovery_enabled")
        val LAST_LINK_SCAN = stringPreferencesKey("last_link_scan")
    }

    val llmConfig: Flow<LlmConfig> = context.settingsStore.data.map { p ->
        LlmConfig(
            protocol = p[Keys.PROTOCOL]?.let { runCatching { LlmProtocol.valueOf(it) }.getOrNull() }
                ?: LlmProtocol.OPENAI_CHAT,
            baseUrl = p[Keys.BASE_URL].orEmpty(),
            model = p[Keys.MODEL].orEmpty(),
            thinkingEnabled = p[Keys.THINKING_ENABLED] ?: false,
            thinkingEffort = p[Keys.THINKING_EFFORT]
                ?.let { value -> ThinkingEffort.entries.firstOrNull { it.name == value } }
                ?: ThinkingEffort.MEDIUM
        )
    }

    suspend fun saveLlm(
        protocol: LlmProtocol,
        baseUrl: String,
        model: String,
        apiKey: String,
        thinkingEnabled: Boolean = false,
        thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM
    ) {
        context.settingsStore.edit { p ->
            p[Keys.PROTOCOL] = protocol.name
            p[Keys.BASE_URL] = baseUrl.trim()
            p[Keys.MODEL] = model.trim()
            p[Keys.THINKING_ENABLED] = thinkingEnabled
            p[Keys.THINKING_EFFORT] = thinkingEffort.name
        }
        if (apiKey.isNotBlank()) llmKeyStore.save(apiKey.trim())
    }

    fun savedLlmApiKey(): String = llmKeyStore.load().orEmpty()

    /** 组装当前端点：DataStore 非密配置 + Keystore 取 Key */
    suspend fun currentEndpoint(): LlmEndpoint {
        val cfg = llmConfig.first()
        return LlmEndpoint(
            baseUrl = cfg.baseUrl,
            apiKey = llmKeyStore.load().orEmpty(),
            model = cfg.model,
            protocol = cfg.protocol,
            thinkingEnabled = cfg.thinkingEnabled,
            thinkingEffort = cfg.thinkingEffort
        )
    }

    // ---- Embedding（独立配置，§9.4） ----

    val embeddingConfig: Flow<EmbeddingEndpoint> = context.settingsStore.data.map { p ->
        EmbeddingEndpoint(
            enabled = p[Keys.EMBED_ENABLED] ?: false,
            baseUrl = p[Keys.EMBED_BASE_URL].orEmpty(),
            apiKey = "", // 不回填到 Flow；读取走 embeddingEndpoint()
            model = p[Keys.EMBED_MODEL].orEmpty()
        )
    }

    suspend fun saveEmbedding(enabled: Boolean, baseUrl: String, model: String, apiKey: String) {
        context.settingsStore.edit { p ->
            p[Keys.EMBED_ENABLED] = enabled
            p[Keys.EMBED_BASE_URL] = baseUrl.trim()
            p[Keys.EMBED_MODEL] = model.trim()
        }
        if (apiKey.isNotBlank()) embedKeyStore.save(apiKey.trim())
    }

    fun savedEmbeddingApiKey(): String = embedKeyStore.load().orEmpty()

    suspend fun embeddingEndpoint(): EmbeddingEndpoint {
        val p = context.settingsStore.data.first()
        return EmbeddingEndpoint(
            enabled = p[Keys.EMBED_ENABLED] ?: false,
            baseUrl = p[Keys.EMBED_BASE_URL].orEmpty(),
            apiKey = embedKeyStore.load().orEmpty(),
            model = p[Keys.EMBED_MODEL].orEmpty()
        )
    }

    // ---- 通用偏好 ----

    val openDirectCapture: Flow<Boolean> =
        context.settingsStore.data.map { it[Keys.DIRECT_CAPTURE] ?: false }

    suspend fun setOpenDirectCapture(value: Boolean) {
        context.settingsStore.edit { it[Keys.DIRECT_CAPTURE] = value }
    }

    val remindLeadMinutes: Flow<Int> =
        context.settingsStore.data.map { it[Keys.REMIND_LEAD]?.toIntOrNull() ?: 5 }

    suspend fun setRemindLeadMinutes(value: Int) {
        context.settingsStore.edit { it[Keys.REMIND_LEAD] = value.coerceIn(0, 24 * 60).toString() }
    }

    /** 待办提醒的通知方式（响铃/振动/静音），默认响铃。 */
    val reminderMode: Flow<ReminderMode> =
        context.settingsStore.data.map { ReminderMode.fromName(it[Keys.REMINDER_MODE]) }

    suspend fun setReminderMode(mode: ReminderMode) {
        context.settingsStore.edit { it[Keys.REMINDER_MODE] = mode.name }
    }

    // ---- 关联发现 ----

    val linkDiscoveryEnabled: Flow<Boolean> =
        context.settingsStore.data.map { it[Keys.LINK_ENABLED] ?: true }

    suspend fun isLinkDiscoveryEnabled(): Boolean = linkDiscoveryEnabled.first()

    suspend fun setLinkDiscoveryEnabled(value: Boolean) {
        context.settingsStore.edit { it[Keys.LINK_ENABLED] = value }
    }

    suspend fun lastLinkScan(): Long =
        context.settingsStore.data.first()[Keys.LAST_LINK_SCAN]?.toLongOrNull() ?: 0L

    suspend fun setLastLinkScan(ts: Long) {
        context.settingsStore.edit { it[Keys.LAST_LINK_SCAN] = ts.toString() }
    }
}
