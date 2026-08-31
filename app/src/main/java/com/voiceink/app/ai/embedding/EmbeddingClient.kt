package com.voiceink.app.ai.embedding

import com.voiceink.app.ai.EmbeddingEndpoint
import com.voiceink.app.core.AppJson
import com.voiceink.app.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedding 客户端（§9.4）：只读独立的 EmbeddingEndpoint 配置，
 * 任何 OpenAI 兼容 /v1/embeddings 服务均可（OpenAI/硅基流动/Jina/Ollama）。
 * 未配置或调用失败均返回 null，由 LinkDiscovery 走降级召回，绝不影响主流程。
 */
@Singleton
class EmbeddingClient @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository
) {
    suspend fun currentEndpoint(): EmbeddingEndpoint = settings.embeddingEndpoint()

    suspend fun embedOrNull(text: String): FloatArray? {
        val ep = settings.embeddingEndpoint()
        if (!ep.enabled || ep.baseUrl.isBlank() || ep.model.isBlank()) return null
        return embed(ep, text)
    }

    /** 测试连接（设置页）：返回向量维度，失败返回 null */
    suspend fun test(ep: EmbeddingEndpoint): Int? {
        if (ep.baseUrl.isBlank() || ep.model.isBlank()) return null
        return embed(ep, "声念连接测试")?.size
    }

    private suspend fun embed(ep: EmbeddingEndpoint, text: String): FloatArray? =
        withContext(Dispatchers.IO) {
            val payload = kotlinx.serialization.json.buildJsonObject {
                put("model", ep.model)
                put("input", text)
            }.toString()
            val req = Request.Builder()
                .url(ep.baseUrl.trimEnd('/') + "/v1/embeddings")
                .header("Authorization", "Bearer ${ep.apiKey}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val root = AppJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["data"]!!.jsonArray.first().jsonObject["embedding"]!!.jsonArray
                        .map { it.jsonPrimitive.float }.toFloatArray()
                }
            }.getOrNull()
        }
}
