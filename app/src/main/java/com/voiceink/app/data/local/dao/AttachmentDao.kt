package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(attachment: NoteAttachmentEntity): Long

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt ASC, id ASC")
    suspend fun listForNote(noteId: Long): List<NoteAttachmentEntity>

    @Query("SELECT COUNT(*) FROM note_attachments WHERE noteId = :noteId")
    suspend fun countForNote(noteId: Long): Int

    @Query("DELETE FROM note_attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun clearForNote(noteId: Long)
}
