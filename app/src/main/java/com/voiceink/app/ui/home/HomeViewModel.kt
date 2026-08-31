package com.voiceink.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.ai.pipeline.AiPipeline
import com.voiceink.app.data.local.entity.NoteEntity
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val todoRepo: TodoRepository,
    private val pipeline: AiPipeline
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = selectedCategory.asStateFlow()

    private val keyword = MutableStateFlow("")
    val keywordState: StateFlow<String> = keyword.asStateFlow()

    val categories: StateFlow<List<String>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 每条笔记的未完成待办数（首页卡片「N 条待办」角标） */
    val todoCounts: StateFlow<Map<Long, Int>> = todoRepo.observeOpenCountsPerNote()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val notes: StateFlow<List<NoteEntity>> =
        combine(selectedCategory, keyword) { c, k -> c to k }
            .flatMapLatest { (c, k) -> repo.observe(c, null, k) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun setKeyword(value: String) {
        keyword.value = value
    }

    /** AI_FAILED 笔记重试整理（§4.3）：重置状态并重新入队 */
    fun retryOrganize(noteId: Long) {
        viewModelScope.launch {
            repo.resetToPending(noteId)
            pipeline.enqueue(noteId)
        }
    }
}
