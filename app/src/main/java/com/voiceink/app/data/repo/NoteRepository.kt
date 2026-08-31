package com.voiceink.app.data.repo

import androidx.room.withTransaction
import com.voiceink.app.ai.prompt.ParsedIntent
import com.voiceink.app.data.local.AppDatabase
import com.voiceink.app.data.local.dao.AttachmentDao
import com.voiceink.app.data.local.dao.CategoryDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
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
    private val categoryDao: CategoryDao,
    private val database: AppDatabase,
    private val todoDao: TodoDao,
    private val attachmentDao: AttachmentDao
) {
    /** 原始输入立即落库，status=PENDING_AI，保证 AI 故障时数据不丢 */
    suspend fun insertRaw(content: String, source: String = "app", intentHint: String? = null): Long =
        noteDao.insert(
            NoteEntity(
                content = content,
                rawContent = content,
                source = source,
                intentHint = intentHint
            )
        )

    fun observe(
        category: String?,
        tag: String? = null,
        keyword: String? = null,
        inspiration: Boolean? = null
    ): Flow<List<NoteEntity>> =
        noteDao.observeFiltered(
            category,
            tag,
            keyword?.takeIf { it.isNotBlank() },
            inspiration
        )

    fun observeById(id: Long): Flow<NoteEntity?> = noteDao.observeById(id)

    suspend fun resetToPending(id: Long) = noteDao.resetToPending(id)

    /** 用户手动改分类：同步更新分类体系 usageCount（§4.2） */
    suspend fun updateCategory(id: Long, category: String?) = database.withTransaction {
        noteDao.updateCategory(id, category)
        category?.takeIf { it.isNotBlank() }?.let {
            categoryDao.insertIfAbsent(CategoryEntity(it, kind = "theme"))
            categoryDao.bumpUsage(it)
        }
    }

    fun categories(): Flow<List<String>> = noteDao.observeCategories()

    suspend fun byId(id: Long): NoteEntity? = noteDao.byId(id)

    suspend fun markFailed(id: Long) = noteDao.markFailed(id)

    suspend fun markFailedIfCurrent(note: NoteEntity): Boolean =
        noteDao.markFailedIfCurrent(
            id = note.id,
            expectedUpdatedAt = note.updatedAt,
            expectedContent = note.content,
            expectedRawContent = note.rawContent,
            expectedIntentHint = note.intentHint
        ) > 0

    suspend fun delete(id: Long) = noteDao.deleteById(id)

    suspend fun deleteIfCurrent(note: NoteEntity): Boolean =
        noteDao.deleteIfCurrent(
            id = note.id,
            expectedUpdatedAt = note.updatedAt,
            expectedContent = note.content,
            expectedRawContent = note.rawContent,
            expectedIntentHint = note.intentHint,
            expectedAttachmentCount = attachmentDao.countForNote(note.id)
        ) > 0

    suspend fun deleteIfCurrentAndDetachTodo(note: NoteEntity, todoId: Long): Boolean =
        database.withTransaction {
            val deleted = noteDao.deleteIfCurrent(
                id = note.id,
                expectedUpdatedAt = note.updatedAt,
                expectedContent = note.content,
                expectedRawContent = note.rawContent,
                expectedIntentHint = note.intentHint,
                expectedAttachmentCount = attachmentDao.countForNote(note.id)
            ) > 0
            if (deleted) todoDao.detachFromNote(todoId)
            deleted
        }

    /** 用户常用分类（按使用频次），注入 Prompt 供 AI 参考（§8.2） */
    suspend fun topCategories(): List<String> = categoryDao.topThemes()

    suspend fun saveDraft(id: Long, title: String, content: String) {
        val normalized = content.trim()
        noteDao.saveDraft(id, title.trim(), normalized, normalized)
    }

    suspend fun prepareForReorganization(id: Long, title: String, content: String) {
        val normalized = content.trim()
        database.withTransaction {
            noteDao.prepareForReorganization(id, title.trim(), normalized, normalized)
            noteDao.clearDiagramsFor(id)
        }
    }

    suspend fun applyOrganization(
        noteId: Long,
        parsed: ParsedIntent.Note,
        expected: NoteEntity? = null
    ): Boolean = database.withTransaction {
        val updated = noteDao.applyOrganization(
            id = noteId,
            title = parsed.title,
            content = parsed.content.ifBlank { "" },
            category = parsed.category,
            type = parsed.type,
            mood = parsed.mood,
            summary = parsed.summary,
            isInspiration = parsed.isInspiration,
            expectedUpdatedAt = expected?.updatedAt,
            expectedContent = expected?.content,
            expectedRawContent = expected?.rawContent,
            expectedIntentHint = expected?.intentHint,
            expectedAttachmentCount = expected?.let { attachmentDao.countForNote(it.id) }
        )
        if (updated == 0) {
            false
        } else {
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
            true
        }
    }
}
