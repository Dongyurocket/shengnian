package com.voiceink.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.data.local.dao.TagDao
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.repo.NoteRepository
import com.voiceink.app.data.repo.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val pipeline: AiPipeline
) : ViewModel() {

    private val noteId: Long = checkNotNull(savedStateHandle["noteId"])

    val note: StateFlow<NoteEntity?> = notes.observeById(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags: StateFlow<List<String>> = tagDao.observeTags(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** AI 从该笔记提炼出的待办（§11.3） */
    val extractedTodos: StateFlow<List<TodoEntity>> = todos.observeForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry() {
        viewModelScope.launch {
            notes.resetToPending(noteId)
            pipeline.enqueue(noteId)
        }
    }

    fun updateCategory(category: String?) {
        viewModelScope.launch { notes.updateCategory(noteId, category) }
    }
}
