package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.NoteSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: NoteSourceEntity): Long

    @Query("SELECT * FROM note_sources WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<NoteSourceEntity>>

    @Query("SELECT * FROM note_sources WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    suspend fun listForNote(noteId: Long): List<NoteSourceEntity>

    @Query("SELECT * FROM note_sources WHERE noteId = :noteId AND url = :url LIMIT 1")
    suspend fun byUrl(noteId: Long, url: String): NoteSourceEntity?

    @Query("DELETE FROM note_sources WHERE noteId = :noteId AND url NOT IN (:urls)")
    suspend fun deleteMissing(noteId: Long, urls: List<String>)

    @Query("DELETE FROM note_sources WHERE noteId = :noteId")
    suspend fun clearForNote(noteId: Long)
}
