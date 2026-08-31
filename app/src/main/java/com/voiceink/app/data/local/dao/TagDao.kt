package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.CategoryEntity
import com.voiceink.app.data.local.entity.NoteTagCrossRef
import com.voiceink.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class NoteSharedCount(val noteId: Long, val shared: Int)
data class NoteTagTotal(val noteId: Long, val total: Int)

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearFor(noteId: Long)

    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId")
    suspend fun tagsOf(noteId: Long): List<String>

    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId")
    fun observeTags(noteId: Long): Flow<List<String>>

    /** 与指定笔记共享标签的其它笔记及其共享标签数（§9.1 候选召回 b 路） */
    @Query("""
        SELECT noteId, COUNT(*) AS shared FROM note_tags
        WHERE tag IN (SELECT tag FROM note_tags WHERE noteId = :noteId)
          AND noteId != :noteId
        GROUP BY noteId
    """)
    suspend fun sharedTagCounts(noteId: Long): List<NoteSharedCount>

    @Query("SELECT COUNT(*) FROM note_tags WHERE noteId = :noteId")
    suspend fun tagCount(noteId: Long): Int

    @Query("SELECT noteId, COUNT(*) AS total FROM note_tags WHERE noteId IN (:ids) GROUP BY noteId")
    suspend fun tagCounts(ids: List<Long>): List<NoteTagTotal>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(category: CategoryEntity): Long

    @Query("UPDATE categories SET usageCount = usageCount + 1 WHERE name = :name")
    suspend fun bumpUsage(name: String)

    @Query("SELECT name FROM categories WHERE kind = 'theme' ORDER BY usageCount DESC, name LIMIT 20")
    suspend fun topThemes(): List<String>
}
