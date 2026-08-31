package com.voiceink.app.ui.capture

import android.net.Uri
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

    var savedCount by mutableIntStateOf(0)
        private set

    var pendingImages by mutableStateOf<List<Uri>>(emptyList())
        private set

    var saving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onTextChange(value: String) {
        text = value
        errorMessage = null
    }

    fun addImages(uris: List<Uri>) {
        pendingImages = (pendingImages + uris).distinct().take(4)
        errorMessage = null
    }

    fun removeImage(uri: Uri) {
        pendingImages = pendingImages - uri
    }

    /** 保存并继续：清空输入框，可立刻输入下一条；AI 异步整理 */
    fun saveAndContinue(intentHint: String? = null) {
        if (saving) return
        val content = text.trim()
        if (content.isEmpty() && pendingImages.isEmpty()) return
        val images = pendingImages
        text = ""
        pendingImages = emptyList()
        errorMessage = null
        saving = true
        viewModelScope.launch {
            try {
                controller.capture(
                    content,
                    source = "app",
                    intentHint = intentHint,
                    imageUris = images
                )
                savedCount++
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                text = listOf(content, text.trim())
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                pendingImages = (images + pendingImages).distinct().take(4)
                errorMessage = "保存失败：${error.message?.take(60) ?: "请稍后重试"}"
            } finally {
                saving = false
            }
        }
    }
}
