package com.voiceink.app.ai.pipeline

import com.voiceink.app.ai.LlmGateway
import com.voiceink.app.ai.LlmRequest
import com.voiceink.app.ai.embedding.EmbeddingClient
import com.voiceink.app.ai.prompt.JsonExtractor
import com.voiceink.app.ai.prompt.Prompts
import com.voiceink.app.data.local.dao.EmbeddingDao
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.entity.NoteEmbeddingEntity
import com.voiceink.app.data.local.entity.NoteLinkEntity
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

// ---- 纯函数（可单测，§9.3 阈值） ----

internal fun cosine(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in 0 until minOf(a.size, b.size)) {
        dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
    }
    return if (na == 0f || nb == 0f) 0f
    else (dot / (sqrt(na.toDouble()) * sqrt(nb.toDouble()))).toFloat()
}

internal fun jaccard(shared: Int, aTotal: Int, bTotal: Int): Float {
    val union = aTotal + bTotal - shared
    return if (union <= 0) 0f else shared.toFloat() / union
}

/** 综合分：LLM 确认 = 0.6·召回分 + 0.4；未确认 = 召回分（§9.1④） */
internal fun combineScore(recall: Float, llmConfirmed: Boolean): Float =
    if (llmConfirmed) 0.6f * recall + 0.4f else recall

internal fun FloatArray.toBytes(): ByteArray =
    ByteBuffer.allocate(size * 4).apply { asFloatBuffer().put(this@toBytes) }.array()

internal fun ByteArray.toFloats(): FloatArray =
    FloatArray(size / 4).also { ByteBuffer.wrap(this).asFloatBuffer().get(it) }

internal const val VECTOR_RECALL_THRESHOLD = 0.72f   // 向量候选召回线
internal const val JACCARD_RECALL_THRESHOLD = 0.34f  // 标签 Jaccard 召回线
internal const val VECTOR_AUTO_LINK = 0.90f          // 向量高置信直通线
internal const val LINK_MIN_SCORE = 0.55f            // 综合分落库线

/**
 * 智能关联发现（§9）：三路候选召回（向量余弦 Top-K / 标签 Jaccard / 实体重合由标签覆盖）
 * → LLM 复核 → 双向写库；向量分 ≥0.90 高置信直通。
 */
@Singleton
class LinkDiscovery @Inject constructor(
    private val notes: NoteDao,
    private val links: LinkDao,
    private val embeddings: EmbeddingDao,
    private val tags: TagDao,
    private val embeddingClient: EmbeddingClient,
    private val gateway: LlmGateway,
    private val settings: com.voiceink.app.data.repo.SettingsRepository
) {
    suspend fun discoverFor(noteId: Long) {
        if (!settings.isLinkDiscoveryEnabled()) return
        val note = notes.byId(noteId) ?: return

        // ① Embedding 计算并入库（失败不阻塞，走降级）
        val ep = embeddingClient.currentEndpoint()
        val newVec = embeddingClient.embedOrNull(note.title + "\n" + note.summary.orEmpty())
            ?.also { embeddings.upsert(NoteEmbeddingEntity(noteId, it.toBytes(), ep.model)) }

        // ② 候选召回（两路并集；上限 20）
        val candidates = mutableMapOf<Long, Float>()
        if (newVec != null) {
            embeddings.all().asSequence()
                .filter { it.noteId != noteId }
                .map { it.noteId to cosine(newVec, it.vector.toFloats()) }
                .filter { it.second >= VECTOR_RECALL_THRESHOLD }
                .sortedByDescending { it.second }
                .take(10)
                .forEach { (id, s) -> candidates.merge(id, s, ::maxOf) }
        }
        // 标签 Jaccard
        val myTagCount = tags.tagCount(noteId)
        val shared = tags.sharedTagCounts(noteId)
        if (myTagCount > 0 && shared.isNotEmpty()) {
            val totals = tags.tagCounts(shared.map { it.noteId }).associate { it.noteId to it.total }
            shared.forEach { s ->
                val total = totals[s.noteId] ?: return@forEach
                val j = jaccard(s.shared, myTagCount, total)
                if (j >= JACCARD_RECALL_THRESHOLD) {
                    candidates.merge(s.noteId, j * 0.9f, ::maxOf)
                }
            }
        }
        if (candidates.isEmpty()) return
        val trimmed = candidates.entries.sortedByDescending { it.value }.take(20)
            .associate { it.toPair() }

        // ③ LLM 复核
        val digests = notes.allDigests(excludeId = noteId).filter { it.id in trimmed.keys }
        if (digests.isEmpty()) return
        val judged = runCatching {
            gateway.complete(
                LlmRequest(
                    system = Prompts.LINK_JUDGE,
                    user = buildString {
                        append("新笔记：标题《").append(note.title).append("》，摘要：")
                        append(note.summary.orEmpty()).append("\n候选笔记：\n")
                        digests.forEach { d ->
                            append("- id=").append(d.id)
                                .append(" 《").append(d.title).append("》 ")
                                .append(d.summary.orEmpty()).append('\n')
                        }
                    },
                    jsonSchemaName = "link",
                    maxTokens = 512
                )
            )
        }.getOrNull()?.let { JsonExtractor.extractLinks(it.text) } ?: emptyList()

        // ④ 落库（双向两行）
        val now = System.currentTimeMillis()
        val rows = mutableListOf<NoteLinkEntity>()
        val judgedIds = judged.map { it.first }.toSet()
        for ((id, reason) in judged) {
            val score = combineScore(trimmed[id] ?: 0f, llmConfirmed = true)
            if (score < LINK_MIN_SCORE) continue
            rows += NoteLinkEntity(noteId, id, score, reason, createdAt = now)
            rows += NoteLinkEntity(id, noteId, score, reason, createdAt = now)
        }
        // 高置信直通：向量分极高即使 LLM 未返回也建链
        trimmed.filter { it.value >= VECTOR_AUTO_LINK && it.key !in judgedIds }
            .forEach { (id, s) ->
                rows += NoteLinkEntity(noteId, id, s, "语义高度相似", createdAt = now)
                rows += NoteLinkEntity(id, noteId, s, "语义高度相似", createdAt = now)
            }
        if (rows.isNotEmpty()) links.upsertAll(rows)
    }
}
