package com.voiceink.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.voiceink.app.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.ai.LlmProtocol
import com.voiceink.app.ai.ThinkingEffort
import com.voiceink.app.reminder.ReminderMode
import com.voiceink.app.update.UpdateInfo
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

    ui.update.available?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = vm::dismissUpdate,
            onDownload = vm::downloadUpdate,
            onOpenPage = vm::openReleasePage
        )
    }

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
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("启用模型思考", fontSize = 13.5.sp, color = Ink)
                    Text("控制支持 reasoning/thinking 的模型", fontSize = 10.5.sp, color = Faint)
                }
                Switch(
                    checked = ui.thinkingEnabled,
                    onCheckedChange = { value -> vm.update { it.copy(thinkingEnabled = value) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            if (ui.thinkingEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ThinkingEffort.entries.forEach { effort ->
                        val selected = ui.thinkingEffort == effort
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                                .background(if (selected) Accent12 else Paper2)
                                .clickable { vm.update { it.copy(thinkingEffort = effort) } }
                        ) {
                            Text(
                                effort.label,
                                fontSize = 11.sp,
                                color = if (selected) Accent else Muted
                            )
                        }
                    }
                }
            }
            if (ui.thinkingEnabled && ui.protocol == LlmProtocol.OPENAI_RESPONSES) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("显示推理摘要", fontSize = 13.5.sp, color = Ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ui.showReasoningSummary,
                        onCheckedChange = { value -> vm.update { it.copy(showReasoningSummary = value) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(Accent)
                        .clickable { vm.testLlm() }
                        .padding(horizontal = 14.dp)
                ) {
                    Text("测试连接", color = Color.White, fontSize = 11.5.sp)
                }
                ui.llmTestResult?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, fontSize = 10.5.sp, color = Muted)
                }
            }
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
            UpdateRow(
                state = ui.update,
                onCheck = vm::checkForUpdates
            )
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(12.dp))
            Text("提醒方式", style = VoiceInkTextStyles.Chip, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ReminderMode.entries.forEach { mode ->
                    val selected = ui.reminderMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                            .background(if (selected) Accent12 else Paper2)
                            .clickable { vm.update { it.copy(reminderMode = mode) } }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = mode.label,
                            style = VoiceInkTextStyles.Chip,
                            color = if (selected) Accent else Muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("待办到点弹出通知，此处控制提醒的声音与振动", fontSize = 10.5.sp, color = Faint)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(Paper2)
                        .clickable(enabled = !ui.rebuilding && ui.linkEnabled) { vm.rebuildNetwork() }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        if (ui.rebuilding) "重建进行中" else "重建知识网络",
                        color = if (ui.rebuilding) Faint else Ink,
                        fontSize = 11.5.sp
                    )
                }
            }
            ui.rebuildMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 11.sp, color = if (ui.rebuilding) Accent else Muted)
                if (ui.rebuilding && ui.rebuildTotal > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ui.rebuildProcessed.toFloat() / ui.rebuildTotal.coerceAtLeast(1) },
                        color = Accent,
                        trackColor = Paper2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ui.rebuildPhase?.let { phase ->
                        Spacer(Modifier.height(3.dp))
                        Text(phase, fontSize = 10.5.sp, color = Faint)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 数据导出（§6.4）：Markdown + JSON 到用户选择目录
            val exportLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let { vm.exportTo(it) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(Paper2)
                        .clickable { exportLauncher.launch(null) }
                        .padding(horizontal = 14.dp)
                ) {
                    Text("导出备份（Markdown + JSON）", color = Ink, fontSize = 11.5.sp)
                }
                ui.exportResult?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, fontSize = 10.5.sp, color = Muted)
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
private fun UpdateRow(
    state: UpdateUiState,
    onCheck: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text("检查更新", fontSize = 13.5.sp, color = Ink)
            Text(
                state.message ?: "当前版本 v${BuildConfig.VERSION_NAME}",
                fontSize = 10.5.sp,
                color = if (state.message?.startsWith("发现") == true) Accent else Faint
            )
        }
        if (state.checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
        } else {
            IconButton(onClick = onCheck) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "检查更新",
                    tint = Accent
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onOpenPage: (UpdateInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("发现新版本 v${info.version}", style = VoiceInkTextStyles.NoteTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = info.notes.ifBlank { "本次版本暂无更新说明。" },
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Ink
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (info.apkUrl != null) {
                        "下载完成后将交给系统安装器处理。"
                    } else {
                        "该版本没有 APK 附件，将打开 GitHub 发布页。"
                    },
                    fontSize = 10.5.sp,
                    color = Faint
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (info.apkUrl != null) onDownload(info) else onOpenPage(info)
                }
            ) {
                Text(if (info.apkUrl != null) "下载更新" else "查看发布页")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
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
