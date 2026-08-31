package com.voiceink.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/** 设置页（§11.3：米灰分组卡 + 墨字，点缀色仅用于关键动作） */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Ink,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text("设置", style = VoiceInkTextStyles.NoteTitle)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(0.1f))
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = "AI 模型") {
            Text("协议", style = VoiceInkTextStyles.Chip, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LlmProtocol.entries.forEach { p ->
                                    val selected = ui.protocol == p
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                            .background(if (selected) Accent12 else Paper2)
                            .clickable { vm.update { it.copy(protocol = p) } }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = when (p) {
                                LlmProtocol.OPENAI_CHAT -> "Chat"
                                LlmProtocol.OPENAI_RESPONSES -> "Responses"
                                LlmProtocol.ANTHROPIC_MESSAGES -> "Anthropic"
                            },
                            style = VoiceInkTextStyles.Chip,
                            color = if (selected) Accent else Muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FormField(
                value = ui.baseUrl,
                onValueChange = { v -> vm.update { it.copy(baseUrl = v) } },
                label = "Base URL",
                placeholder = "https://api.openai.com"
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = ui.apiKey,
                onValueChange = { v -> vm.update { it.copy(apiKey = v) } },
                label = "API Key",
                placeholder = "sk-…（仅存本机 Keystore）",
                secret = true
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = ui.model,
                onValueChange = { v -> vm.update { it.copy(model = v) } },
                label = "模型",
                placeholder = "gpt-4o-mini / deepseek-chat / claude-sonnet-4-5"
            )
        }

        Spacer(Modifier.height(14.dp))
        // Embedding 独立配置（§9.4）：可折叠，默认关闭
        SectionCard(title = "语义向量（Embedding）") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用语义关联", fontSize = 13.5.sp, color = Ink)
                    Text(
                        "未配置时关联发现使用标签 + AI 分析模式",
                        fontSize = 10.5.sp, color = Faint
                    )
                }
                Switch(
                    checked = ui.embedEnabled,
                    onCheckedChange = { v -> vm.update { it.copy(embedEnabled = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            if (ui.embedEnabled) {
                Spacer(Modifier.height(12.dp))
                FormField(
                    value = ui.embedBaseUrl,
                    onValueChange = { v -> vm.update { it.copy(embedBaseUrl = v) } },
                    label = "Base URL",
                    placeholder = "https://api.siliconflow.cn 或 http://192.168.1.5:11434"
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = ui.embedApiKey,
                    onValueChange = { v -> vm.update { it.copy(embedApiKey = v) } },
                    label = "API Key",
                    placeholder = "同样经 Keystore 加密存储",
                    secret = true
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = ui.embedModel,
                    onValueChange = { v -> vm.update { it.copy(embedModel = v) } },
                    label = "模型",
                    placeholder = "text-embedding-3-small / bge-m3 / nomic-embed-text"
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                            .background(Accent)
                            .clickable { vm.testEmbedding() }
                            .padding(horizontal = 14.dp)
                    ) {
                        Text("测试连接", color = Color.White, fontSize = 11.5.sp)
                    }
                    ui.embedTestResult?.let {
                        Spacer(Modifier.width(10.dp))
                        Text(it, fontSize = 10.5.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("更换模型后需「重建知识网络」以重算向量", fontSize = 10.5.sp, color = Faint)
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = "通用") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("打开 App 直接进速记", fontSize = 13.5.sp, color = Ink)
                    Text("极速记录偏好", fontSize = 10.5.sp, color = Faint)
                }
                Switch(
                    checked = ui.openDirectCapture,
                    onCheckedChange = { v -> vm.update { it.copy(openDirectCapture = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("笔记关联发现", fontSize = 13.5.sp, color = Ink)
                    Text("新笔记自动建立语义双向链接", fontSize = 10.5.sp, color = Faint)
                }
                Switch(
                    checked = ui.linkEnabled,
                    onCheckedChange = { v -> vm.update { it.copy(linkEnabled = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            Spacer(Modifier.height(12.dp))
            FormField(
                value = ui.remindLead,
                onValueChange = { v -> vm.update { it.copy(remindLead = v.filter(Char::isDigit)) } },
                label = "默认提前提醒（分钟）",
                placeholder = "5",
                number = true
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(Paper2)
                        .clickable(enabled = !ui.rebuilding) { vm.rebuildNetwork() }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        if (ui.rebuilding) "已加入后台重建队列" else "重建知识网络",
                        color = if (ui.rebuilding) Faint else Ink,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(VoiceInkRadius.Input))
                .background(Accent)
                .clickable { vm.save() }
        ) {
            Text(
                if (ui.saved) "已保存 ✓" else "保存",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard)
            .padding(18.dp)
    ) {
        Text(
            text = title,
            style = VoiceInkTextStyles.SectionLabel,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        content()
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    secret: Boolean = false,
    number: Boolean = false
) {
    Column {
        Text(label, style = VoiceInkTextStyles.Chip, color = Muted)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.5.sp, color = Faint) },
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            shape = RoundedCornerShape(VoiceInkRadius.Input),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Paper2,
                unfocusedContainerColor = Paper2,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
