package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.NoteDiagramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(diagram: NoteDiagramEntity): Long

    @Query("SELECT * FROM note_diagrams WHERE noteId = :noteId ORDER BY updatedAt DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteDiagramEntity>>

    @Query("SELECT * FROM note_diagrams WHERE noteId = :noteId ORDER BY updatedAt DESC")
    suspend fun listForNote(noteId: Long): List<NoteDiagramEntity>

    @Query("DELETE FROM note_diagrams WHERE noteId = :noteId")
    suspend fun clearForNote(noteId: Long)
}
