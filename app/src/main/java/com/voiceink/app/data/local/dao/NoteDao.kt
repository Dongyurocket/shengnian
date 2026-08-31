package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.voiceink.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

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

    // 阶段 1 的列表筛选：仅分类；标签/关键词在阶段 4 接入完整版
    @Query("SELECT * FROM notes WHERE (:category IS NULL OR category = :category) ORDER BY createdAt DESC")
    fun observeFiltered(category: String?): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT category FROM notes WHERE category IS NOT NULL ORDER BY category")
    fun observeCategories(): Flow<List<String>>
}
