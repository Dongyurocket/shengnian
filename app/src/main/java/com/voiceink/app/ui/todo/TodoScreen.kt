package com.voiceink.app.ui.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voiceink.app.ui.theme.Muted

/** 占位：阶段 3 实现（设计稿屏 04） */
@Composable
fun TodoScreen() {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text("智能待办 · 阶段 3 实现", color = Muted)
    }
}
