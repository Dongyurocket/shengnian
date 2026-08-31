package com.voiceink.app.data.repo

import com.voiceink.app.ai.prompt.ParsedIntent
import com.voiceink.app.data.local.dao.CategoryDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.entity.CategoryEntity
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteTagCrossRef
import com.voiceink.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao
) {
    /** 原始输入立即落库，status=PENDING_AI，保证 AI 故障时数据不丢 */
    suspend fun insertRaw(content: String, source: String = "app", intentHint: String? = null): Long =
        noteDao.insert(NoteEntity(content = content, source = source, intentHint = intentHint))

    fun observe(category: String?): Flow<List<NoteEntity>> = noteDao.observeFiltered(category)

    fun categories(): Flow<List<String>> = noteDao.observeCategories()

    suspend fun byId(id: Long): NoteEntity? = noteDao.byId(id)

    suspend fun markFailed(id: Long) = noteDao.markFailed(id)

    suspend fun delete(id: Long) = noteDao.deleteById(id)

    /** 用户常用分类（按使用频次），注入 Prompt 供 AI 参考（§8.2） */
    suspend fun topCategories(): List<String> = categoryDao.topThemes()

    /** 把 AI 整理结果写回笔记：标题/正文/分类/类型/情绪/标签/摘要 → status=READY */
    suspend fun applyOrganization(noteId: Long, parsed: ParsedIntent.Note) {
        noteDao.applyOrganization(
            id = noteId,
            title = parsed.title,
            content = parsed.content.ifBlank { "" },
            category = parsed.category,
            type = parsed.type,
            mood = parsed.mood,
            summary = parsed.summary
        )
        tagDao.clearFor(noteId)
        if (parsed.tags.isNotEmpty()) {
            tagDao.insertTags(parsed.tags.map { TagEntity(it) })
            tagDao.link(parsed.tags.map { NoteTagCrossRef(noteId, it) })
        }
        parsed.category?.let {
            categoryDao.insertIfAbsent(CategoryEntity(it, kind = "theme"))
            categoryDao.bumpUsage(it)
        }
        parsed.type?.let {
            categoryDao.insertIfAbsent(CategoryEntity(it, kind = "type"))
            categoryDao.bumpUsage(it)
        }
    }
}
