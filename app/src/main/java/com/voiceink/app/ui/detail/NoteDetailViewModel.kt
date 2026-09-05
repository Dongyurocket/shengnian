package com.voiceink.app.ui.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.diagram.DiagramGenerator
import com.voiceink.app.ai.diagram.DiagramKind
import com.voiceink.app.ai.ImagePayloadEncoder
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.ai.pipeline.AiProgress
import com.voiceink.app.ai.pipeline.AiSummaryStore
import com.voiceink.app.ai.pipeline.progressForWork
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.data.repo.SettingsRepository
import kotlinx.coroutines.flow.combine
import com.voiceink.app.ai.pipeline.requiresNoteIntent
import com.voiceink.app.data.local.dao.DiagramDao
import com.voiceink.app.data.local.dao.LinkDao
import com.voiceink.app.data.local.dao.RelatedNote
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteLifecycleStatus
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.repo.NoteAttachmentRepository
import com.voiceink.app.data.repo.NoteRepository
import com.voiceink.app.data.repo.NoteSourceRepository
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    todos: TodoRepository,
    private val tagDao: TagDao,
    private val linkDao: LinkDao,
    private val pipeline: AiPipeline,
    private val attachmentRepo: NoteAttachmentRepository,
    private val sourceRepo: NoteSourceRepository,
    private val diagramDao: DiagramDao,
    private val diagramGenerator: DiagramGenerator,
    summaries: AiSummaryStore,
    settings: SettingsRepository
) : ViewModel() {

    private val noteId: Long = checkNotNull(savedStateHandle["noteId"])

    val aiProgress = combine(
        pipeline.observeWork(noteId), summaries.summaries, settings.llmConfig
    ) { info, currentSummaries, config ->
        val summary = if (config.thinkingEnabled && config.showReasoningSummary &&
            config.protocol == LlmProtocol.OPENAI_RESPONSES
        ) currentSummaries[info?.id?.toString()].orEmpty() else ""
        progressForWork(info, summary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProgress())

    val note: StateFlow<NoteEntity?> = notes.observeById(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags: StateFlow<List<String>> = tagDao.observeTags(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** AI 从该笔记提炼出的待办（§11.3） */
    val extractedTodos: StateFlow<List<TodoEntity>> = todos.observeForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 相关笔记（§9：双向链接，按综合分排序） */
    val related: StateFlow<List<RelatedNote>> = linkDao.observeRelated(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attachments = attachmentRepo.observeForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources = sourceRepo.observeForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagrams = diagramDao.observeForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class DiagramUiState(
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _diagramState = MutableStateFlow(DiagramUiState())
    val diagramState: StateFlow<DiagramUiState> = _diagramState

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> = _attachmentError

    private val _attachmentBusy = MutableStateFlow(false)
    val attachmentBusy: StateFlow<Boolean> = _attachmentBusy

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    /** 手动解除关联（双向删除） */
    fun unlink(otherId: Long) {
        viewModelScope.launch { linkDao.deleteBidirectional(noteId, otherId) }
    }

    fun delete(onDone: () -> Unit = {}) {
        if (_deleting.value) return
        viewModelScope.launch {
            _deleting.value = true
            _deleteError.value = null
            try {
                notes.delete(noteId)
                onDone()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _deleteError.value = error.message ?: "删除失败"
            } finally {
                _deleting.value = false
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val hint = note.value?.intentHint
            notes.resetToPending(noteId)
            pipeline.enqueue(
                noteId,
                forceNote = requiresNoteIntent(hint)
            )
        }
    }

    fun saveDraft(title: String, content: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            notes.saveDraft(noteId, title, content)
            onDone()
        }
    }

    fun reorganize(title: String, content: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            notes.prepareForReorganization(noteId, title, content)
            pipeline.enqueue(noteId, forceNote = true)
            onDone()
        }
    }

    fun addAttachment(uri: Uri) {
        if (attachments.value.size >= ImagePayloadEncoder.MAX_IMAGES) {
            _attachmentError.value = "最多保留 ${ImagePayloadEncoder.MAX_IMAGES} 张图片"
            return
        }
        if (_attachmentBusy.value) return
        viewModelScope.launch {
            _attachmentBusy.value = true
            try {
                attachmentRepo.copyFromUri(noteId, uri)
                _attachmentError.value = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _attachmentError.value = error.message ?: "图片保存失败"
            } finally {
                _attachmentBusy.value = false
            }
        }
    }

    fun clearAttachmentError() {
        _attachmentError.value = null
    }

    fun removeAttachment(attachment: com.voiceink.app.data.local.entity.NoteAttachmentEntity) {
        viewModelScope.launch {
            attachmentRepo.delete(attachment)
            _attachmentError.value = null
        }
    }

    fun generateDiagram(kind: DiagramKind) {
        if (_diagramState.value.loading) return
        viewModelScope.launch {
            _diagramState.value = DiagramUiState(loading = true)
            try {
                diagramGenerator.generate(noteId, kind)
                _diagramState.value = DiagramUiState()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _diagramState.value = DiagramUiState(error = error.message ?: "图表生成失败")
            }
        }
    }

    fun clearDiagramError() {
        _diagramState.value = DiagramUiState()
    }

    fun updateCategory(category: String?) {
        viewModelScope.launch { notes.updateCategory(noteId, category) }
    }

    fun updateLifecycleStatus(status: NoteLifecycleStatus) {
        viewModelScope.launch { notes.updateLifecycleStatus(noteId, status) }
    }
}
