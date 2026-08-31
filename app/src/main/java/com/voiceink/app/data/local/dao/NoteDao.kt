package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.voiceink.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

data class NoteDigest(val id: Long, val title: String, val summary: String?)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    // 列表筛选：分类/标签/关键词全部可选（§4.2）
    @Query("""
        SELECT DISTINCT n.* FROM notes n
        LEFT JOIN note_tags nt ON nt.noteId = n.id
        WHERE (:category IS NULL OR n.category = :category)
          AND (:tag IS NULL OR nt.tag = :tag)
          AND (:inspiration IS NULL OR n.isInspiration = :inspiration)
          AND (:lifecycleStatus IS NULL OR n.lifecycleStatus = :lifecycleStatus)
          AND (:hasOpenTodo = 0 OR EXISTS (
              SELECT 1 FROM todos open_todo
              WHERE open_todo.sourceNoteId = n.id AND open_todo.done = 0
          ))
          AND (:keyword IS NULL OR n.title LIKE '%'||:keyword||'%'
               OR n.content LIKE '%'||:keyword||'%' OR nt.tag LIKE '%'||:keyword||'%'
               OR EXISTS (
                   SELECT 1 FROM todos t
                   WHERE t.sourceNoteId = n.id
                     AND t.content LIKE '%'||:keyword||'%'
               ))
        ORDER BY n.updatedAt DESC, n.id DESC
    """)
    fun observeFiltered(
        category: String?,
        tag: String? = null,
        keyword: String? = null,
        inspiration: Boolean? = null,
        lifecycleStatus: com.voiceink.app.data.local.entity.NoteLifecycleStatus? = null,
        hasOpenTodo: Boolean = false
    ): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("UPDATE notes SET status = 'PENDING_AI', updatedAt = :now WHERE id = :id")
    suspend fun resetToPending(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET category = :category, updatedAt = :now WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET lifecycleStatus = :lifecycleStatus, updatedAt = :now WHERE id = :id")
    suspend fun updateLifecycleStatus(
        id: Long,
        lifecycleStatus: com.voiceink.app.data.local.entity.NoteLifecycleStatus,
        now: Long = System.currentTimeMillis()
    )

    @Query("SELECT id, title, summary FROM notes WHERE status = 'READY' AND id != :excludeId")
    suspend fun allDigests(excludeId: Long): List<NoteDigest>

    @Query("SELECT id FROM notes WHERE status = 'READY' AND createdAt > :since")
    suspend fun readyIdsSince(since: Long): List<Long>

    // ---- 洞察页聚合（§11.4，纯 Room） ----

    @Query("SELECT createdAt FROM notes")
    fun observeAllCreatedAt(): Flow<List<Long>>

    @Query("SELECT DISTINCT category FROM notes WHERE category IS NOT NULL ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("UPDATE notes SET status = 'AI_FAILED', updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: Long, now: Long = System.currentTimeMillis())

    /** 只把失败状态写回到发起请求后没有被用户改动的那一版笔记。 */
    @Query("""
        UPDATE notes SET status = 'AI_FAILED', updatedAt = :now
        WHERE id = :id AND status IN ('PENDING_AI', 'AI_FAILED')
          AND updatedAt = :expectedUpdatedAt
          AND content = :expectedContent
          AND rawContent = :expectedRawContent
          AND ((intentHint = :expectedIntentHint) OR
               (intentHint IS NULL AND :expectedIntentHint IS NULL))
    """)
    suspend fun markFailedIfCurrent(
        id: Long,
        expectedUpdatedAt: Long,
        expectedContent: String,
        expectedRawContent: String,
        expectedIntentHint: String?,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("""
        UPDATE notes SET title = :title, content = :content, rawContent = :rawContent,
            status = 'READY', intentHint = NULL, updatedAt = :now WHERE id = :id
    """)
    suspend fun saveDraft(
        id: Long,
        title: String,
        content: String,
        rawContent: String,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE notes SET title = :title, content = :content, rawContent = :rawContent,
            status = 'PENDING_AI', intentHint = 'note', updatedAt = :now WHERE id = :id
    """)
    suspend fun prepareForReorganization(
        id: Long,
        title: String,
        content: String,
        rawContent: String,
        now: Long = System.currentTimeMillis()
    )

    /** 返回受影响行数，0 表示请求期间笔记已被用户改动。 */
    @Query("""
        UPDATE notes SET title = :title, content = :content, category = :category,
            type = :type, mood = :mood, summary = :summary, isInspiration = :isInspiration,
            status = 'READY', updatedAt = :now
        WHERE id = :id AND status IN ('PENDING_AI', 'AI_FAILED')
          AND (
            :expectedUpdatedAt IS NULL OR (
                updatedAt = :expectedUpdatedAt
                AND content = :expectedContent
                AND rawContent = :expectedRawContent
                AND ((intentHint = :expectedIntentHint) OR
                     (intentHint IS NULL AND :expectedIntentHint IS NULL))
                AND (SELECT COUNT(*) FROM note_attachments WHERE noteId = :id) = :expectedAttachmentCount
            )
          )
    """)
    suspend fun applyOrganization(
        id: Long,
        title: String,
        content: String,
        category: String?,
        type: String?,
        mood: String?,
        summary: String?,
        isInspiration: Boolean,
        expectedUpdatedAt: Long?,
        expectedContent: String?,
        expectedRawContent: String?,
        expectedIntentHint: String?,
        expectedAttachmentCount: Int?,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("DELETE FROM note_diagrams WHERE noteId = :id")
    suspend fun clearDiagramsFor(id: Long)

    @Query("""
        DELETE FROM notes
        WHERE id = :id AND status IN ('PENDING_AI', 'AI_FAILED')
          AND updatedAt = :expectedUpdatedAt
          AND content = :expectedContent
          AND rawContent = :expectedRawContent
          AND ((intentHint = :expectedIntentHint) OR
               (intentHint IS NULL AND :expectedIntentHint IS NULL))
          AND (SELECT COUNT(*) FROM note_attachments WHERE noteId = :id) = :expectedAttachmentCount
    """)
    suspend fun deleteIfCurrent(
        id: Long,
        expectedUpdatedAt: Long,
        expectedContent: String,
        expectedRawContent: String,
        expectedIntentHint: String?,
        expectedAttachmentCount: Int
    ): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
