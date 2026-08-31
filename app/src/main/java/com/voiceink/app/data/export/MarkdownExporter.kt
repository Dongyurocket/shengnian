package com.voiceink.app.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.local.dao.AttachmentDao
import com.voiceink.app.data.local.dao.DiagramDao
import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.dao.SourceDao
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.dao.TodoDao
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import com.voiceink.app.data.local.entity.NoteDiagramEntity
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteSourceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(
    val noteCount: Int,
    val attachmentFailures: Int
)

@Serializable
private data class AttachmentExport(
    val id: Long,
    val localPath: String,
    val mimeType: String,
    val displayName: String,
    val exportedPath: String?,
    val error: String?
)

@Serializable
private data class SourceExport(
    val id: Long,
    val url: String,
    val title: String,
    val excerpt: String,
    val status: String,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class DiagramExport(
    val id: Long,
    val kind: String,
    val title: String,
    val specJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class NoteExport(
    val id: Long,
    val title: String,
    val content: String,
    val rawContent: String,
    val category: String?,
    val type: String?,
    val mood: String?,
    val summary: String?,
    val isInspiration: Boolean,
    val status: String,
    val source: String,
    val intentHint: String?,
    val tags: List<String>,
    val attachments: List<AttachmentExport>,
    val sources: List<SourceExport>,
    val diagrams: List<DiagramExport>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class TodoExport(
    val id: Long,
    val content: String,
    val priority: Int,
    val deadline: Long?,
    val remindAt: Long?,
    val remindLeadMinutes: Int,
    val done: Boolean,
    val sourceNoteId: Long?,
    val createdAt: Long
)

@Serializable
private data class ExportBundle(
    val exportedAt: Long,
    val notes: List<NoteExport>,
    val todos: List<TodoExport>,
    val attachmentFailures: List<String>
)

/**
 * 备份导出：每条笔记一个 Markdown 文件，另附全量 JSON 和 attachments/目录。
 * 所有文件由用户通过 SAF 选择目录，API Key 等配置不会进入备份。
 */
@Singleton
class MarkdownExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val todoDao: TodoDao,
    private val tagDao: TagDao,
    private val attachmentDao: AttachmentDao,
    private val sourceDao: SourceDao,
    private val diagramDao: DiagramDao,
    private val json: Json
) {
    private val exportMutex = Mutex()

    suspend fun exportAll(treeUri: Uri): ExportResult = exportMutex.withLock {
        withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: error("无法打开所选目录")
        val notes = noteDao.observeAll().first()
        val todos = todoDao.observeAll().first()
        val attachmentDir = dir.findFile("attachments")?.takeIf { it.isDirectory }
            ?: dir.createDirectory("attachments")
        val failures = mutableListOf<String>()
        val exportedNotes = mutableListOf<NoteExport>()

        notes.forEach { note ->
            val tags = tagDao.tagsOf(note.id)
            val attachments = attachmentDao.listForNote(note.id)
            val sources = sourceDao.listForNote(note.id)
            val diagrams = diagramDao.listForNote(note.id)
            val noteAttachments = attachments.map { attachment ->
                val copiedPath = copyAttachment(attachmentDir, note, attachment)
                val error = if (copiedPath == null) {
                    val message = "笔记 ${note.id} 的附件 ${attachment.displayName} 复制失败"
                    failures += message
                    message
                } else null
                AttachmentExport(
                    id = attachment.id,
                    localPath = attachment.localPath,
                    mimeType = attachment.mimeType,
                    displayName = attachment.displayName,
                    exportedPath = copiedPath,
                    error = error
                )
            }
            val noteExport = NoteExport(
                id = note.id,
                title = note.title,
                content = note.content,
                rawContent = note.rawContent,
                category = note.category,
                type = note.type,
                mood = note.mood,
                summary = note.summary,
                isInspiration = note.isInspiration,
                status = note.status.name,
                source = note.source,
                intentHint = note.intentHint,
                tags = tags,
                attachments = noteAttachments,
                sources = sources.map { it.toExport() },
                diagrams = diagrams.map { it.toExport() },
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
            exportedNotes += noteExport
            writeMarkdown(dir, note, tags, noteAttachments, sources, diagrams, todos)
        }

        val bundle = ExportBundle(
            exportedAt = System.currentTimeMillis(),
            notes = exportedNotes,
            todos = todos.map { todo ->
                TodoExport(
                    id = todo.id,
                    content = todo.content,
                    priority = todo.priority,
                    deadline = todo.deadline,
                    remindAt = todo.remindAt,
                    remindLeadMinutes = todo.remindLeadMinutes,
                    done = todo.done,
                    sourceNoteId = todo.sourceNoteId,
                    createdAt = todo.createdAt
                )
            },
            attachmentFailures = failures
        )
        writeTextFile(
            dir,
            "application/json",
            "shengnian-backup.json",
            json.encodeToString(ExportBundle.serializer(), bundle)
        )
        ExportResult(notes.size, failures.size)
        }
    }

    private fun writeMarkdown(
        dir: DocumentFile,
        note: NoteEntity,
        tags: List<String>,
        attachments: List<AttachmentExport>,
        sources: List<NoteSourceEntity>,
        diagrams: List<NoteDiagramEntity>,
        todos: List<com.voiceink.app.data.local.entity.TodoEntity>
    ) {
        val noteTodos = todos.filter { it.sourceNoteId == note.id }
        val safeTitle = safeFilePart(note.title.ifBlank { "未命名" }, 30)
        val fileName = "${TimeUtils.formatForFile(note.createdAt)}-${note.id}-$safeTitle.md"
        val md = buildString {
            append("---\n")
            append("title: ").append(yaml(note.title)).append('\n')
            append("created: ").append(yaml(TimeUtils.formatDateTime(note.createdAt))).append('\n')
            append("inspiration: ").append(note.isInspiration).append('\n')
            append("status: ").append(note.status.name).append('\n')
            append("source: ").append(yaml(note.source)).append('\n')
            note.category?.let { append("category: ").append(yaml(it)).append('\n') }
            note.type?.let { append("type: ").append(yaml(it)).append('\n') }
            note.mood?.let { append("mood: ").append(yaml(it)).append('\n') }
            if (tags.isNotEmpty()) {
                append("tags: [")
                append(tags.joinToString(", ") { yaml(it) })
                append("]\n")
            }
            if (sources.isNotEmpty()) {
                append("source_urls: [")
                append(sources.joinToString(", ") { yaml(it.url) })
                append("]\n")
            }
            if (attachments.isNotEmpty()) {
                append("attachments: [")
                append(attachments.joinToString(", ") { yaml(it.exportedPath ?: it.localPath) })
                append("]\n")
            }
            note.summary?.let { append("summary: ").append(yaml(it)).append('\n') }
            append("---\n\n")
            append(note.content).append('\n')
            if (note.rawContent != note.content) {
                append("\n## 原始输入\n\n").append(note.rawContent).append('\n')
            }
            if (sources.isNotEmpty()) {
                append("\n## 来源链接\n")
                sources.forEach { source ->
                    append("- ").append(source.url).append("（").append(source.status).append("）")
                    if (source.title.isNotBlank()) append(" ").append(source.title)
                    if (!source.error.isNullOrBlank()) append("：").append(source.error)
                    append('\n')
                }
            }
            if (attachments.any { it.error != null }) {
                append("\n## 附件导出提示\n")
                attachments.filter { it.error != null }.forEach { append("- ").append(it.error).append('\n') }
            }
            if (diagrams.isNotEmpty()) {
                append("\n## AI 图表\n")
                diagrams.forEach { diagram ->
                    append("- ").append(diagram.kind).append("：").append(diagram.title).append('\n')
                }
            }
            if (noteTodos.isNotEmpty()) {
                append("\n## 提炼待办\n")
                noteTodos.forEach { todo ->
                    append(if (todo.done) "- [x] " else "- [ ] ").append(todo.content).append('\n')
                }
            }
        }
        writeTextFile(dir, "text/markdown", fileName, md)
    }

    private fun copyAttachment(
        targetDir: DocumentFile?,
        note: NoteEntity,
        attachment: NoteAttachmentEntity
    ): String? {
        if (targetDir == null) return null
        val source = File(attachment.localPath)
        if (!source.isFile) return null
        val fileName = buildString {
            append(note.id).append('-')
            append(attachment.id).append('-')
            append(source.length()).append('-')
            append(safeFilePart(attachment.displayName, 80))
        }
        targetDir.findFile(fileName)?.takeIf { it.isFile }?.let {
            return "attachments/$fileName"
        }
        val tempName = ".$fileName.tmp"
        val temp = targetDir.findFile(tempName)?.takeIf { it.isFile }
            ?: targetDir.createFile(attachment.mimeType, tempName)
            ?: return null
        return try {
            context.contentResolver.openOutputStream(temp.uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: error("无法打开导出文件")
            if (!temp.renameTo(fileName)) error("无法完成附件导出")
            "attachments/$fileName"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { temp.delete() }
            null
        }
    }

    private fun writeTextFile(dir: DocumentFile, mime: String, name: String, content: String) {
        val file = dir.findFile(name)?.takeIf { it.isFile }
            ?: dir.createFile(mime, name)
            ?: error("无法创建导出文件：$name")
        context.contentResolver.openOutputStream(file.uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入导出文件：$name")
    }

    private fun safeFilePart(value: String, max: Int): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        .trim()
        .ifBlank { "未命名" }
        .take(max)

    private fun yaml(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace('\n', ' ')
        .replace('\r', ' ') + "\""

    private fun NoteSourceEntity.toExport() = SourceExport(
        id = id,
        url = url,
        title = title,
        excerpt = excerpt,
        status = status,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun NoteDiagramEntity.toExport() = DiagramExport(
        id = id,
        kind = kind,
        title = title,
        specJson = specJson,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
