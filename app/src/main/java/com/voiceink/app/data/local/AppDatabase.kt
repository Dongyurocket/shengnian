package com.voiceink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
