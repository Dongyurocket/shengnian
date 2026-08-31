package com.voiceink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceink.app.data.local.entity.CategoryEntity
import com.voiceink.app.data.local.entity.NoteTagCrossRef
import com.voiceink.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

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
