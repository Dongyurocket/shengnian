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
          AND (:keyword IS NULL OR n.title LIKE '%'||:keyword||'%'
               OR n.content LIKE '%'||:keyword||'%' OR nt.tag LIKE '%'||:keyword||'%')
        ORDER BY n.createdAt DESC
    """)
    fun observeFiltered(category: String?, tag: String?, keyword: String?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("UPDATE notes SET status = 'PENDING_AI', updatedAt = :now WHERE id = :id")
    suspend fun resetToPending(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET category = :category, updatedAt = :now WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String?, now: Long = System.currentTimeMillis())

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

    @Query("""UPDATE notes SET title = :title, content = :content, category = :category,
        type = :type, mood = :mood, summary = :summary,
        status = 'READY', updatedAt = :now WHERE id = :id""")
    suspend fun applyOrganization(
        id: Long,
        title: String,
        content: String,
        category: String?,
        type: String?,
        mood: String?,
        summary: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
