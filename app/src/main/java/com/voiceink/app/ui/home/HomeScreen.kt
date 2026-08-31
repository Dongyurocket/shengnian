package com.voiceink.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 屏 01 首页 · 灵感笔记流。
 * 阶段 1：仅原文展示；PENDING_AI 的笔记以原文前 40 字为临时标题并带「整理中」角标。
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 6.dp, bottom = 180.dp
        )
    ) {
        item { BrandRow(onOpenSettings) }
        item { SearchBox() }
        item {
            FilterChips(
                categories = categories,
                selected = selected,
                totalCount = notes.size,
                onSelect = vm::selectCategory
            )
        }

        if (notes.isEmpty()) {
            item { EmptyHint() }
        } else {
            val grouped = notes.groupBy { TimeUtils.dayLabel(it.createdAt) }
            for (label in listOf("今天", "昨天", "更早")) {
                val dayNotes = grouped[label] ?: continue
                item { SectionLabel(label) }
                items(dayNotes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = { onOpenNote(note.id) })
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
        // 右上角「念」头像 → 设置页
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
                fontFamily = com.voiceink.app.ui.theme.SerifFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
    }
}

@Composable
private fun SearchBox() {
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
        Text("搜索灵感、标签或待办", fontSize = 13.5.sp, color = Faint)
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
private fun NoteCard(note: NoteEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Card))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        val pending = note.status != NoteStatus.READY
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
        // 元信息行：时间 + 分类 + 状态角标
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
            if (pending) {
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(23.dp)
                        .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                        .background(Accent12)
                        .padding(horizontal = 9.dp)
                ) {
                    Text("整理中", style = VoiceInkTextStyles.Chip, color = Accent)
                }
            }
        }
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
private fun EmptyHint() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
    ) {
        Text(
            "声落成章，念起成行",
            fontFamily = com.voiceink.app.ui.theme.SerifFamily,
            fontSize = 18.5.sp,
            color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Text("点下方「记录灵感」，说出或写下第一条想法", fontSize = 12.5.sp, color = Faint)
    }
}
