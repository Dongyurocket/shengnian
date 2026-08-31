package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.NoteEmbeddingEntity
import com.voiceink.app.data.local.entity.NoteLinkEntity
import kotlinx.coroutines.flow.Flow

data class RelatedNote(
    val id: Long,
    val title: String,
    val content: String,
    val category: String?,
    val summary: String?,
    val score: Float,
    val reason: String?
)

data class NoteLinkPair(val fromId: Long, val toId: Long)

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<NoteLinkEntity>)

    @Query("DELETE FROM note_links WHERE (fromId = :a AND toId = :b) OR (fromId = :b AND toId = :a)")
    suspend fun deleteBidirectional(a: Long, b: Long)

    @Query("DELETE FROM note_links")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM note_links")
    suspend fun count(): Int

    @Query("SELECT fromId, toId FROM note_links")
    fun observeAllLinks(): Flow<List<NoteLinkPair>>

    @Query(
        """
        SELECT n.id, n.title, n.content, n.category, n.summary, l.score, l.reason
        FROM note_links l JOIN notes n ON n.id = l.toId
        WHERE l.fromId = :noteId ORDER BY l.score DESC
    """
    )
    fun observeRelated(noteId: Long): Flow<List<RelatedNote>>
}

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: NoteEmbeddingEntity)

    @Query("SELECT * FROM note_embeddings")
    suspend fun all(): List<NoteEmbeddingEntity>

    @Query("SELECT * FROM note_embeddings WHERE noteId = :id")
    suspend fun byId(id: Long): NoteEmbeddingEntity?

    @Query("DELETE FROM note_embeddings WHERE noteId = :id")
    suspend fun deleteForNote(id: Long)

    @Query("DELETE FROM note_embeddings")
    suspend fun clear()
}
