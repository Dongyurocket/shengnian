package com.voiceink.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 保留 v3 用户数据的增量迁移；新增表均由 noteId 外键级联清理。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN rawContent TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN isInspiration INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE notes SET rawContent = content WHERE rawContent = ''")
        db.execSQL("UPDATE notes SET isInspiration = 1 WHERE type = '灵感'")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noteId INTEGER NOT NULL,
                localPath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                displayName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_attachments_noteId ON note_attachments(noteId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noteId INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                excerpt TEXT NOT NULL,
                status TEXT NOT NULL,
                error TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_sources_noteId_url ON note_sources(noteId, url)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_diagrams (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noteId INTEGER NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                specJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_diagrams_noteId ON note_diagrams(noteId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_diagrams_noteId_kind ON note_diagrams(noteId, kind)")
    }
}

/** 增量迁移：为待办增加多次提醒、系统闹钟和日历同步字段。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN lifecycleStatus TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_lifecycleStatus ON notes(lifecycleStatus)")

        db.execSQL("ALTER TABLE todos ADD COLUMN reminderCount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE todos ADD COLUMN reminderIntervalMinutes INTEGER NOT NULL DEFAULT 10")
        db.execSQL("ALTER TABLE todos ADD COLUMN isAlarm INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE todos ADD COLUMN calendarEventId INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todo_reminders (
                todoId INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                triggerAt INTEGER NOT NULL,
                PRIMARY KEY(todoId, sequence),
                FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_reminders_triggerAt ON todo_reminders(triggerAt)")
        db.execSQL(
            """
            INSERT INTO todo_reminders(todoId, sequence, triggerAt)
            SELECT id, 0, remindAt FROM todos WHERE remindAt IS NOT NULL
            """.trimIndent()
        )
    }
}
