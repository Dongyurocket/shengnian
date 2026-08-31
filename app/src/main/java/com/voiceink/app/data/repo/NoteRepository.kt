package com.voiceink.app.data.repo

import com.voiceink.app.data.local.dao.NoteDao
import com.voiceink.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    /** 原始输入立即落库，status=PENDING_AI，保证 AI 故障时数据不丢 */
    suspend fun insertRaw(content: String, source: String = "app", intentHint: String? = null): Long =
        noteDao.insert(NoteEntity(content = content, source = source, intentHint = intentHint))

    fun observe(category: String?): Flow<List<NoteEntity>> = noteDao.observeFiltered(category)

    fun categories(): Flow<List<String>> = noteDao.observeCategories()

    suspend fun byId(id: Long): NoteEntity? = noteDao.byId(id)
}
