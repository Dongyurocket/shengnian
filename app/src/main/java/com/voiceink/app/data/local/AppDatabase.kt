package com.voiceink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voiceink.app.data.local.dao.AttachmentDao
import com.voiceink.app.data.local.dao.CategoryDao
import com.voiceink.app.data.local.dao.DiagramDao
import com.voiceink.app.data.local.dao.EmbeddingDao
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.SourceDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
import com.voiceink.app.data.local.dao.TodoReminderDao
import com.voiceink.app.data.local.entity.CategoryEntity
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import com.voiceink.app.data.local.entity.NoteDiagramEntity
import com.voiceink.app.data.local.entity.NoteEmbeddingEntity
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteLinkEntity
import com.voiceink.app.data.local.entity.NoteSourceEntity
import com.voiceink.app.data.local.entity.NoteTagCrossRef
import com.voiceink.app.data.local.entity.TagEntity
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.local.entity.TodoReminderEntity

@Database(
    entities = [
        NoteEntity::class,
        TodoEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        CategoryEntity::class,
        NoteLinkEntity::class,
        NoteEmbeddingEntity::class,
        NoteAttachmentEntity::class,
        NoteSourceEntity::class,
        NoteDiagramEntity::class,
        TodoReminderEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun todoDao(): TodoDao
    abstract fun todoReminderDao(): TodoReminderDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao
    abstract fun linkDao(): LinkDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun sourceDao(): SourceDao
    abstract fun diagramDao(): DiagramDao
}
