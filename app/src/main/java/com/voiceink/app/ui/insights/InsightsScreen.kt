package com.voiceink.app.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voiceink.app.ui.theme.Muted

/** 占位：阶段 6 实现（设计稿屏 05） */
@Composable
fun InsightsScreen() {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text("洞察 · 阶段 6 实现", color = Muted)
    }
}
