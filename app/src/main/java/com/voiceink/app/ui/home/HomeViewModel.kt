package com.voiceink.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.ai.pipeline.requiresNoteIntent
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.NoteLinkPair
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteLifecycleStatus
import com.voiceink.app.data.repo.NoteMergeController
import com.voiceink.app.data.repo.NoteRepository
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NoteListMode(val label: String) {
    AGGREGATE("聚合"),
    RELATED("关联分类"),
    FOLDER("文件夹")
}

data class NoteSection(val label: String, val notes: List<NoteEntity>)

private data class NoteFilters(
    val category: String?,
    val keyword: String,
    val inspiration: Boolean,
    val lifecycleStatus: NoteLifecycleStatus?,
    val hasOpenTodo: Boolean
)

fun buildNoteSections(
    notes: List<NoteEntity>,
    mode: NoteListMode,
    links: List<NoteLinkPair>
): List<NoteSection> {
    if (notes.isEmpty()) return emptyList()
    return when (mode) {
        NoteListMode.AGGREGATE -> {
            val grouped = notes.groupBy { com.voiceink.app.core.TimeUtils.dayLabel(it.createdAt) }
            listOf("今天", "昨天", "更早").mapNotNull { label ->
                grouped[label]?.let { NoteSection(label, it) }
            }
        }
        NoteListMode.FOLDER -> {
            notes.groupBy { it.category?.takeIf(String::isNotBlank) ?: "未分类" }
                .entries
                .sortedWith(compareBy<Map.Entry<String, List<NoteEntity>>> { it.key == "未分类" }.thenBy { it.key })
                .map { (folder, items) -> NoteSection("文件夹 · $folder", items) }
        }
        NoteListMode.RELATED -> buildRelatedSections(notes, links)
    }
}

private fun buildRelatedSections(
    notes: List<NoteEntity>,
    links: List<NoteLinkPair>
): List<NoteSection> {
    val noteById = notes.associateBy { it.id }
    val visibleIds = noteById.keys
    val adjacency = visibleIds.associateWith { mutableSetOf<Long>() }
    links.forEach { link ->
        if (link.fromId != link.toId && link.fromId in visibleIds && link.toId in visibleIds) {
            adjacency.getValue(link.fromId).add(link.toId)
            adjacency.getValue(link.toId).add(link.fromId)
        }
    }

    val visited = mutableSetOf<Long>()
    val connected = mutableListOf<List<NoteEntity>>()
    val isolated = mutableListOf<NoteEntity>()
    notes.forEach { note ->
        if (!visited.add(note.id)) return@forEach
        val queue = ArrayDeque<Long>()
        val componentIds = mutableListOf<Long>()
        queue.add(note.id)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            componentIds += id
            adjacency.getValue(id).forEach { neighbor ->
                if (visited.add(neighbor)) queue.add(neighbor)
            }
        }
        val component = componentIds.mapNotNull(noteById::get)
            .sortedByDescending { it.createdAt }
        if (component.size == 1) isolated += component.single() else connected += component
    }

    val sections = connected
        .sortedByDescending { group -> group.maxOf { it.createdAt } }
        .mapIndexed { index, group ->
            NoteSection("关联组 ${index + 1} · ${noteLabel(group.first())}", group)
        }
    return if (isolated.isNotEmpty()) sections + NoteSection("未关联", isolated) else sections
}

private fun noteLabel(note: NoteEntity): String =
    (note.title.ifBlank { note.content.replace("\n", " ") }).take(18)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val todoRepo: TodoRepository,
    private val pipeline: AiPipeline,
    private val mergeController: NoteMergeController,
    private val linkDao: LinkDao
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = selectedCategory.asStateFlow()

    private val inspirationOnly = MutableStateFlow(false)
    val inspiration: StateFlow<Boolean> = inspirationOnly.asStateFlow()

    private val selecting = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = selecting.asStateFlow()

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = selection.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _merging = MutableStateFlow(false)
    val merging: StateFlow<Boolean> = _merging.asStateFlow()

    private val keyword = MutableStateFlow("")
    val keywordState: StateFlow<String> = keyword.asStateFlow()

    private val _listMode = MutableStateFlow(NoteListMode.AGGREGATE)
    val listMode: StateFlow<NoteListMode> = _listMode.asStateFlow()

    private val lifecycle = MutableStateFlow<NoteLifecycleStatus?>(null)
    val lifecycleStatus: StateFlow<NoteLifecycleStatus?> = lifecycle.asStateFlow()

    private val openTodoOnly = MutableStateFlow(false)
    val hasOpenTodo: StateFlow<Boolean> = openTodoOnly.asStateFlow()

    val allNotes: StateFlow<List<NoteEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 每条笔记的未完成待办数（首页卡片「N 条待办」角标） */
    val todoCounts: StateFlow<Map<Long, Int>> = todoRepo.observeOpenCountsPerNote()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val linkPairs: StateFlow<List<NoteLinkPair>> = linkDao.observeAllLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relatedCounts: StateFlow<Map<Long, Int>> = linkPairs
        .map { pairs ->
            val neighbors = mutableMapOf<Long, MutableSet<Long>>()
            pairs.forEach { pair ->
                if (pair.fromId != pair.toId) {
                    neighbors.getOrPut(pair.fromId) { mutableSetOf() }.add(pair.toId)
                    neighbors.getOrPut(pair.toId) { mutableSetOf() }.add(pair.fromId)
                }
            }
            neighbors.mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val notes: StateFlow<List<NoteEntity>> =
        combine(selectedCategory, keyword, inspirationOnly, lifecycle, openTodoOnly) { category, text, inspiration, status, hasTodo ->
            NoteFilters(category, text, inspiration, status, hasTodo)
        }
            .flatMapLatest { filters ->
                repo.observe(
                    filters.category,
                    null,
                    filters.keyword,
                    filters.inspiration,
                    filters.lifecycleStatus,
                    filters.hasOpenTodo
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sections: StateFlow<List<NoteSection>> = combine(notes, linkPairs, _listMode) { currentNotes, links, mode ->
        buildNoteSections(currentNotes, mode, links)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun setInspirationOnly(value: Boolean) {
        inspirationOnly.value = value
    }

    fun setOpenTodoOnly(value: Boolean) {
        openTodoOnly.value = value
    }

    fun clearFilters() {
        selectedCategory.value = null
        inspirationOnly.value = false
        lifecycle.value = null
        openTodoOnly.value = false
    }

    fun setSelectionMode(enabled: Boolean) {
        selecting.value = enabled
        if (!enabled) selection.value = emptySet()
    }

    fun toggleSelection(noteId: Long) {
        selecting.value = true
        selection.update { current ->
            if (noteId in current) current - noteId else current + noteId
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun mergeSelected() {
        if (_merging.value) return
        val ids = selection.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            _merging.value = true
            try {
                mergeController.merge(ids)
                selection.value = emptySet()
                selecting.value = false
                _actionMessage.value = "已创建合并笔记，AI 整理中…"
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionMessage.value = error.message ?: "合并失败"
            } finally {
                _merging.value = false
            }
        }
    }

    fun setKeyword(value: String) {
        keyword.value = value
    }

    fun setListMode(mode: NoteListMode) {
        _listMode.value = mode
    }

    fun selectLifecycleStatus(status: NoteLifecycleStatus?) {
        lifecycle.value = status
    }

    fun updateLifecycleStatus(noteId: Long, status: NoteLifecycleStatus) {
        viewModelScope.launch {
            repo.updateLifecycleStatus(noteId, status)
            _actionMessage.value = "已标记为「${status.label}」"
        }
    }

    fun updateCategory(noteId: Long, category: String?) {
        viewModelScope.launch {
            repo.updateCategory(noteId, category)
            _actionMessage.value = if (category == null) "已移出文件夹" else "已归入「$category」"
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch {
            try {
                repo.delete(note.id)
                selection.update { it - note.id }
                _actionMessage.value = "已删除笔记"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _actionMessage.value = error.message ?: "删除失败"
            }
        }
    }

    /** AI_FAILED 笔记重试整理（§4.3）：重置状态并重新入队 */
    fun retryOrganize(noteId: Long) {
        viewModelScope.launch {
            val hint = repo.byId(noteId)?.intentHint
            repo.resetToPending(noteId)
            pipeline.enqueue(
                noteId,
                forceNote = requiresNoteIntent(hint)
            )
        }
    }
}
