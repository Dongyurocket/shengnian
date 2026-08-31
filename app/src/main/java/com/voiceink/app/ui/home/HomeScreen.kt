package com.voiceink.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.data.local.entity.NoteStatus
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SerifFamily
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 屏 01 首页 · 灵感笔记流。
 * PENDING_AI 笔记以原文前 40 字为临时标题并带「整理中」角标；
 * AI_FAILED 带「整理失败 · 点击重试」角标（§4.3）。
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenNote: (Long) -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val keyword by vm.keywordState.collectAsStateWithLifecycle()
    val todoCounts by vm.todoCounts.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 180.dp)
    ) {
        item { BrandRow(onOpenSettings) }
        item { SearchBox(keyword, vm::setKeyword) }
        item {
            FilterChips(
                categories = categories,
                selected = selected,
                totalCount = notes.size,
                onSelect = vm::selectCategory
            )
        }

        if (notes.isEmpty()) {
            item { EmptyHint(searching = keyword.isNotBlank() || selected != null) }
        } else {
            val grouped = notes.groupBy { TimeUtils.dayLabel(it.createdAt) }
            for (label in listOf("今天", "昨天", "更早")) {
                val dayNotes = grouped[label] ?: continue
                item { SectionLabel(label) }
                items(dayNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        openTodos = todoCounts[note.id] ?: 0,
                        onClick = { onOpenNote(note.id) },
                        onRetry = { vm.retryOrganize(note.id) }
                    )
                    Spacer(Modifier.height(11.dp))
                }
            }
        }
    }
}

@Composable
private fun BrandRow(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text("声念", style = VoiceInkTextStyles.BrandName)
            Spacer(Modifier.height(6.dp))
            Text("声落成章，念起成行", style = VoiceInkTextStyles.BrandSlogan)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Accent06)
                .border(1.dp, Accent12, CircleShape)
                .clickable(onClick = onOpenSettings)
        ) {
            Text(
                "念",
                fontFamily = SerifFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
    }
}

/** 搜索框（§4.1：可输入关键词，匹配标题/正文/标签） */
@Composable
private fun SearchBox(keyword: String, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Input))
            .background(Paper2)
            .border(1.dp, Line, RoundedCornerShape(VoiceInkRadius.Input))
            .padding(horizontal = 13.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Faint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = keyword,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = Ink),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (keyword.isEmpty()) {
                    Text("搜索灵感、标签或待办", fontSize = 13.5.sp, color = Faint)
                }
                inner()
            }
        )
    }
}

@Composable
private fun FilterChips(
    categories: List<String>,
    selected: String?,
    totalCount: Int,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        FilterChip(
            text = "全部 $totalCount",
            selected = selected == null,
            onClick = { onSelect(null) }
        )
        for (c in categories) {
            FilterChip(text = c, selected = selected == c, onClick = { onSelect(c) })
        }
    }
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
            .background(if (selected) Ink else Paper2)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp)
    ) {
        Text(
            text = text,
            style = VoiceInkTextStyles.Chip,
            color = if (selected) Color.White else Muted
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = VoiceInkTextStyles.SectionLabel,
        modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
    )
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    openTodos: Int,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Card))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        val title = if (note.title.isNotBlank()) note.title
        else note.content.replace("\n", " ").take(40)
        Text(
            text = title,
            style = VoiceInkTextStyles.NoteTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val summary = note.summary
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = summary,
                style = VoiceInkTextStyles.NoteSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(TimeUtils.timeOfDay(note.createdAt), style = VoiceInkTextStyles.Meta)
            note.category?.let {
                Spacer(Modifier.width(8.dp))
                MetaChip(text = it)
            }
            when (note.status) {
                NoteStatus.PENDING_AI -> {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("整理中", onClick = null)
                }
                NoteStatus.AI_FAILED -> {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("整理失败 · 点击重试", onClick = onRetry)
                }
                NoteStatus.READY -> Unit
            }
            if (openTodos > 0) {
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.CheckBox,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("$openTodos 条待办", fontSize = 10.5.sp, color = Accent)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, onClick: (() -> Unit)?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
            .background(Accent12)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp)
    ) {
        Text(text, style = VoiceInkTextStyles.Chip, color = Accent)
    }
}

@Composable
fun MetaChip(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Chip))
            .background(Paper2)
            .padding(horizontal = 9.dp)
    ) {
        Text(text, style = VoiceInkTextStyles.Chip, color = Muted)
    }
}

@Composable
private fun EmptyHint(searching: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
    ) {
        Text(
            if (searching) "没有找到匹配的笔记" else "声落成章，念起成行",
            fontFamily = SerifFamily,
            fontSize = 18.5.sp,
            color = Muted
        )
        if (!searching) {
            Spacer(Modifier.height(10.dp))
            Text("点下方「记录灵感」，说出或写下第一条想法", fontSize = 12.5.sp, color = Faint)
        }
    }
}
