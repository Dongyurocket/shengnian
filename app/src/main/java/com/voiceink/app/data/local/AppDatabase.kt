package com.voiceink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voiceink.app.data.local.dao.CategoryDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
import com.voiceink.app.data.local.entity.CategoryEntity
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteTagCrossRef
import com.voiceink.app.data.local.entity.TagEntity
import com.voiceink.app.data.local.entity.TodoEntity

@Database(
    entities = [
        NoteEntity::class,
        TodoEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        CategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun todoDao(): TodoDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao
}
