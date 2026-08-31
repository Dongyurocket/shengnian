package com.voiceink.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test
    fun migrateV3DatabaseToV5PreservesDataAndCreatesTables() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-${System.currentTimeMillis()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        context.deleteDatabase(name)

        createV3Database(file)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            val migrated = database.openHelper.writableDatabase
            migrated.query("SELECT rawContent, isInspiration, lifecycleStatus FROM notes WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧正文", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("PENDING", cursor.getString(2))
            }
            migrated.query("SELECT reminderCount, reminderIntervalMinutes, isAlarm, calendarEventId FROM todos WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(10, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertTrue(cursor.isNull(3))
            }
            migrated.query("SELECT sequence, triggerAt FROM todo_reminders WHERE todoId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(4_000_000L, cursor.getLong(1))
            }
            migrated.query("PRAGMA index_list('note_sources')").use { cursor ->
                var foundUniqueUrlIndex = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) ==
                        "index_note_sources_noteId_url"
                    ) {
                        foundUniqueUrlIndex = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                    }
                }
                assertTrue(foundUniqueUrlIndex)
            }
            migrated.query("PRAGMA foreign_key_list('note_attachments')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            }
            migrated.query("PRAGMA foreign_key_list('todo_reminders')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV3Database(file: java.io.File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("""
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT,
                    type TEXT,
                    mood TEXT,
                    summary TEXT,
                    status TEXT NOT NULL,
                    source TEXT NOT NULL,
                    intentHint TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_notes_category ON notes(category)")
            db.execSQL("CREATE INDEX index_notes_createdAt ON notes(createdAt)")
            db.execSQL("CREATE INDEX index_notes_status ON notes(status)")
            db.execSQL("""
                CREATE TABLE todos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    deadline INTEGER,
                    remindAt INTEGER,
                    remindLeadMinutes INTEGER NOT NULL,
                    done INTEGER NOT NULL,
                    sourceNoteId INTEGER,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_todos_deadline ON todos(deadline)")
            db.execSQL("CREATE INDEX index_todos_done ON todos(done)")
            db.execSQL("CREATE TABLE tags (name TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)")
            db.execSQL("""
                CREATE TABLE note_tags (
                    noteId INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY(noteId, tag),
                    FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE categories (
                    name TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    usageCount INTEGER NOT NULL,
                    userCreated INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE note_links (
                    fromId INTEGER NOT NULL,
                    toId INTEGER NOT NULL,
                    score REAL NOT NULL,
                    reason TEXT,
                    autoCreated INTEGER NOT NULL,
                    confirmed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(fromId, toId),
                    FOREIGN KEY(fromId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(toId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_note_links_toId ON note_links(toId)")
            db.execSQL("""
                CREATE TABLE note_embeddings (
                    noteId INTEGER NOT NULL PRIMARY KEY,
                    vector BLOB NOT NULL,
                    model TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.insertOrThrow("notes", null, ContentValues().apply {
                put("title", "旧标题")
                put("content", "旧正文")
                putNull("category")
                put("type", "灵感")
                putNull("mood")
                putNull("summary")
                put("status", "READY")
                put("source", "app")
                putNull("intentHint")
                put("createdAt", 1L)
                put("updatedAt", 2L)
            })
            db.insertOrThrow("todos", null, ContentValues().apply {
                put("content", "旧待办")
                put("priority", 1)
                putNull("deadline")
                put("remindAt", 4_000_000L)
                put("remindLeadMinutes", 5)
                put("done", 0)
                put("sourceNoteId", 1L)
                put("createdAt", 3L)
            })
            db.execSQL("PRAGMA user_version = 3")
        } finally {
            db.close()
        }
    }
}
