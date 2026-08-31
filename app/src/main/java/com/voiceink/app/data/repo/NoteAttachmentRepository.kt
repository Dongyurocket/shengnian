package com.voiceink.app.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.voiceink.app.ai.ImagePayloadEncoder
import com.voiceink.app.ai.LlmImage
import com.voiceink.app.data.local.dao.AttachmentDao
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteAttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao
) {
    fun observeForNote(noteId: Long): Flow<List<NoteAttachmentEntity>> = dao.observeForNote(noteId)

    suspend fun copyFromUri(noteId: Long, uri: Uri): NoteAttachmentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val displayName = queryDisplayName(uri) ?: "图片-${System.currentTimeMillis()}"
        val dir = File(context.filesDir, "note-attachments").apply { mkdirs() }
        val extension = mime.substringAfter('/', "jpeg").replace(Regex("[^A-Za-z0-9]"), "")
        val file = File(dir, "${UUID.randomUUID()}.$extension")
        try {
            val input = resolver.openInputStream(uri) ?: error("无法读取图片")
            input.use { source ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_FILE_BYTES) error("图片超过 10 MB")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val id = dao.insert(
                NoteAttachmentEntity(
                    noteId = noteId,
                    localPath = file.absolutePath,
                    mimeType = mime,
                    displayName = displayName
                )
            )
            NoteAttachmentEntity(
                id = id,
                noteId = noteId,
                localPath = file.absolutePath,
                mimeType = mime,
                displayName = displayName
            )
        } catch (cancelled: CancellationException) {
            file.delete()
            throw cancelled
        } catch (error: Exception) {
            file.delete()
            // 保留失败附件的元数据，详情页仍可提示/删除，后续重试不会丢失记录。
            try {
                dao.insert(
                    NoteAttachmentEntity(
                        noteId = noteId,
                        localPath = file.absolutePath,
                        mimeType = mime,
                        displayName = displayName
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 原始复制错误仍需向调用方报告；元数据写入失败不覆盖它。
            }
            throw error
        }
    }

    suspend fun copyAllFromUris(noteId: Long, uris: List<Uri>): List<NoteAttachmentEntity> =
        uris.take(ImagePayloadEncoder.MAX_IMAGES).mapNotNull { uri ->
            try {
                copyFromUri(noteId, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    suspend fun delete(attachment: NoteAttachmentEntity) = withContext(Dispatchers.IO) {
        dao.delete(attachment.id)
        File(attachment.localPath).delete()
    }

    suspend fun deleteForNote(noteId: Long) = withContext(Dispatchers.IO) {
        val rows = dao.listForNote(noteId)
        dao.clearForNote(noteId)
        rows.forEach { File(it.localPath).delete() }
    }

    suspend fun attachmentCount(noteId: Long): Int = dao.countForNote(noteId)

    suspend fun listForNote(noteId: Long): List<NoteAttachmentEntity> = withContext(Dispatchers.IO) {
        dao.listForNote(noteId)
    }

    suspend fun deleteFiles(attachments: List<NoteAttachmentEntity>) = withContext(Dispatchers.IO) {
        attachments.forEach { File(it.localPath).delete() }
    }

    suspend fun imagesForLlm(noteId: Long): List<LlmImage> = withContext(Dispatchers.IO) {
        ImagePayloadEncoder.encodeAll(
            dao.listForNote(noteId).map { File(it.localPath) to it.mimeType }
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    companion object {
        const val MAX_FILE_BYTES = 10L * 1024 * 1024
        const val IMAGE_ONLY_PLACEHOLDER = "请识别附加图片中的内容。"
    }
}
