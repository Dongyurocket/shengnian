package com.voiceink.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.local.entity.NoteStatus
import com.voiceink.app.ui.home.MetaChip
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Ink2
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Line2
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SerifFamily
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 屏 03 笔记详情页：AI 摘要卡（紫罗兰淡底）+ 整理正文 + 标签行
 * +「AI 提炼的待办」列表（已加入态）+ AI_FAILED 重试入口（§4.3）。
 * 「相关笔记」区在阶段 5 接入（RelatedNotesSection）。
 */
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    vm: NoteDetailViewModel = hiltViewModel()
) {
    val note by vm.note.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val todos by vm.extractedTodos.collectAsStateWithLifecycle()
    var editingCategory by remember { mutableStateOf(false) }

    val n = note
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶部导航
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Ink2,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(6.dp)
            )
        }

        if (n == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("笔记不存在或已转为待办", color = Muted, fontSize = 13.5.sp)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            // meta：时间 · 分类 · 类型
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(TimeUtils.dayLabel(n.createdAt))
                        append(" ")
                        append(TimeUtils.timeOfDay(n.createdAt))
                        n.category?.let { append(" · ").append(it) }
                        n.type?.let { append(" · ").append(it) }
                        n.mood?.let { append(" · ").append(it) }
                    },
                    fontSize = 10.5.sp, color = Faint, letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 9.dp)
                )
            }

            Text(
                text = n.title.ifBlank { n.content.replace("\n", " ").take(40) },
                fontFamily = SerifFamily,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 33.sp,
                color = Ink
            )

            // 标签行：分类（可编辑）+ 标签
            Row(
                modifier = Modifier
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(if (n.category != null) Paper2 else Accent06)
                        .clickable { editingCategory = true }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        n.category ?: "未分类 · 点我设置",
                        style = VoiceInkTextStyles.Chip,
                        color = if (n.category != null) Muted else Accent
                    )
                }
                for (t in tags) {
                    MetaChip(text = t)
                }
            }

            // AI 摘要卡（紫罗兰淡底，§11.4）
            if (!n.summary.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 17.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Input))
                        .background(Accent06)
                        .border(1.dp, Accent12, RoundedCornerShape(VoiceInkRadius.Input))
                        .padding(horizontal = 15.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "AI 摘要",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            color = Accent
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(n.summary!!, fontSize = 12.5.sp, lineHeight = 23.sp, color = Ink2)
                }
            }

            // 整理正文（分段渲染）
            Column(Modifier.padding(top = 17.dp)) {
                n.content.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
                    .forEachIndexed { i, para ->
                        Text(
                            text = para.trim(),
                            fontSize = 13.sp,
                            lineHeight = 24.sp,
                            color = Ink2,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 13.dp)
                        )
                    }
            }

            // 状态：整理中 / 失败重试
            when (n.status) {
                NoteStatus.PENDING_AI -> StatusBar("AI 整理中…", null)
                NoteStatus.AI_FAILED -> StatusBar("整理失败，点击重试") { vm.retry() }
                NoteStatus.READY -> Unit
            }

            // AI 提炼的待办（§11.3：一键加入/已加入态；流水线已自动加入，展示状态）
            if (todos.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(top = 18.dp)
                        .border(0.dp, Color.Transparent)
                ) {
                    androidx.compose.material3.HorizontalDivider(color = Line, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("AI 提炼的待办", style = VoiceInkTextStyles.SectionLabel)
                        Text("${todos.size} 条", fontSize = 10.5.sp, color = Faint)
                    }
                    todos.forEach { todo ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp)
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (todo.done) Faint else Accent.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                todo.content,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                color = if (todo.done) Muted else Ink,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (todo.done) Paper2 else Accent06)
                                    .border(
                                        1.dp,
                                        if (todo.done) Line else Accent12,
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    if (todo.done) "已完成" else "已加入",
                                    fontSize = 10.5.sp,
                                    color = if (todo.done) Faint else Accent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (editingCategory) {
        CategoryEditDialog(
            current = note?.category,
            onDismiss = { editingCategory = false },
            onSave = { vm.updateCategory(it); editingCategory = false }
        )
    }
}

@Composable
private fun StatusBar(text: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Input))
            .background(Accent06)
            .border(1.dp, Accent12, RoundedCornerShape(VoiceInkRadius.Input))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = Accent)
    }
}

@Composable
private fun CategoryEditDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("编辑分类", style = VoiceInkTextStyles.NoteTitle) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("如：产品 / 阅读 / 生活", fontSize = 13.5.sp, color = Faint) },
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
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim().ifBlank { null }) }) {
                Text("保存", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
        }
    )
}
