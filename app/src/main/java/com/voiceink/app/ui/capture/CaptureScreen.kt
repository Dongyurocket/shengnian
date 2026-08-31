package com.voiceink.app.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voiceink.app.ui.theme.Muted

/** 占位：任务 1.5 实现（设计稿屏 02） */
@Composable
fun CaptureScreen(onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text("速记页 · 任务 1.5 实现", color = Muted)
    }
}
