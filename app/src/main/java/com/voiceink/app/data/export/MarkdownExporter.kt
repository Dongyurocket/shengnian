package com.voiceink.app.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class NoteExport(
    val id: Long,
    val title: String,
    val content: String,
    val category: String?,
    val type: String?,
    val mood: String?,
    val summary: String?,
    val status: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class TodoExport(
    val id: Long,
    val content: String,
    val priority: Int,
    val deadline: Long?,
    val done: Boolean,
    val sourceNoteId: Long?,
    val createdAt: Long
)

@Serializable
private data class ExportBundle(
    val exportedAt: Long,
    val notes: List<NoteExport>,
    val todos: List<TodoExport>
)

/**
 * 备份导出（§0.7 / 任务 6.4）：每条笔记一个 Markdown 文件（含 frontmatter），
 * 另附一份全量 JSON（笔记/待办），写入用户选择的目录（SAF）。
 */
@Singleton
class MarkdownExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val todoDao: TodoDao,
    private val tagDao: TagDao,
    private val json: Json
) {
    /** 导出到用户选择的目录，返回笔记数量 */
    suspend fun exportAll(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: error("无法打开所选目录")
        val notes = noteDao.observeAll().first()
        val todos = todoDao.observeAll().first()

        notes.forEach { note ->
            val tags = tagDao.tagsOf(note.id)
            val noteTodos = todos.filter { it.sourceNoteId == note.id }
            val safeTitle = note.title.ifBlank { "未命名" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(30)
            val fileName = "${TimeUtils.formatForFile(note.createdAt)}-$safeTitle.md"
            val md = buildString {
                append("---\n")
                append("title: ").append(note.title).append('\n')
                append("created: ").append(TimeUtils.formatDateTime(note.createdAt)).append('\n')
                note.category?.let { append("category: ").append(it).append('\n') }
                note.type?.let { append("type: ").append(it).append('\n') }
                note.mood?.let { append("mood: ").append(it).append('\n') }
                if (tags.isNotEmpty()) append("tags: [").append(tags.joinToString(", ")).append("]\n")
                note.summary?.let { append("summary: ").append(it).append('\n') }
                append("---\n\n")
                append(note.content).append('\n')
                if (noteTodos.isNotEmpty()) {
                    append("\n## 提炼待办\n")
                    noteTodos.forEach { t ->
                        append(if (t.done) "- [x] " else "- [ ] ").append(t.content).append('\n')
                    }
                }
            }
            dir.createFile("text/markdown", fileName)?.uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    out.write(md.toByteArray(Charsets.UTF_8))
                }
            }
        }

        val bundle = ExportBundle(
            exportedAt = System.currentTimeMillis(),
            notes = notes.map { n ->
                NoteExport(
                    id = n.id, title = n.title, content = n.content,
                    category = n.category, type = n.type, mood = n.mood, summary = n.summary,
                    status = n.status.name, tags = tagDao.tagsOf(n.id),
                    createdAt = n.createdAt, updatedAt = n.updatedAt
                )
            },
            todos = todos.map { t ->
                TodoExport(
                    id = t.id, content = t.content, priority = t.priority,
                    deadline = t.deadline, done = t.done,
                    sourceNoteId = t.sourceNoteId, createdAt = t.createdAt
                )
            }
        )
        dir.createFile("application/json", "shengnian-backup.json")?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(json.encodeToString(ExportBundle.serializer(), bundle).toByteArray(Charsets.UTF_8))
            }
        }

        notes.size
    }
}
