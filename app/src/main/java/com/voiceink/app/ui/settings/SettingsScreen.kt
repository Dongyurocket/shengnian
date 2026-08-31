package com.voiceink.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voiceink.app.ui.theme.Muted

/** 占位：任务 1.4 实现 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text("设置 · 任务 1.4 实现", color = Muted)
    }
}
