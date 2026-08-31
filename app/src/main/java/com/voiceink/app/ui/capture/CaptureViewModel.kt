package com.voiceink.app.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.app.capture.CaptureController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val controller: CaptureController
) : ViewModel() {

    var text by mutableStateOf("")
        private set

    /** 每次成功保存 +1，UI 据此短暂反馈「已保存，AI 整理中…」 */
    var savedCount by mutableIntStateOf(0)
        private set

    fun onTextChange(value: String) {
        text = value
    }

    /** 保存并继续：清空输入框，可立刻输入下一条；AI 异步整理 */
    fun saveAndContinue(intentHint: String? = null) {
        val content = text.trim()
        if (content.isEmpty()) return
        text = ""
        viewModelScope.launch {
            controller.capture(content, source = "app", intentHint = intentHint)
            savedCount++
        }
    }
}
