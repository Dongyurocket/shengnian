package com.voiceink.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.ai.pipeline.requiresNoteIntent
import com.voiceink.app.data.local.entity.NoteEntity
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val todoRepo: TodoRepository,
    private val pipeline: AiPipeline,
    private val mergeController: NoteMergeController
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

    val categories: StateFlow<List<String>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 每条笔记的未完成待办数（首页卡片「N 条待办」角标） */
    val todoCounts: StateFlow<Map<Long, Int>> = todoRepo.observeOpenCountsPerNote()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val notes: StateFlow<List<NoteEntity>> =
        combine(selectedCategory, keyword, inspirationOnly) { c, k, i -> Triple(c, k, i) }
            .flatMapLatest { (c, k, i) -> repo.observe(c, null, k, i) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun setInspirationOnly(value: Boolean) {
        inspirationOnly.value = value
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
