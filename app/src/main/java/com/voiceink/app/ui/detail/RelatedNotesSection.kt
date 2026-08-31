package com.voiceink.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.data.local.dao.RelatedNote
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 详情页「相关笔记」横向卡片区（§11.4）：
 * 显示相关性强度与关联理由，可点击跳转、可手动解除关联。
 */
@Composable
fun RelatedNotesSection(
    related: List<RelatedNote>,
    onOpen: (Long) -> Unit,
    onUnlink: (Long) -> Unit
) {
    Column(Modifier.padding(top = 18.dp)) {
        HorizontalDivider(color = Line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("相关笔记", style = VoiceInkTextStyles.SectionLabel)
            Text("${related.size} 条", fontSize = 10.5.sp, color = Faint)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            related.forEach { r ->
                RelatedCard(r, onOpen = { onOpen(r.id) }, onUnlink = { onUnlink(r.id) })
            }
        }
    }
}

@Composable
private fun RelatedCard(
    note: RelatedNote,
    onOpen: () -> Unit,
    onUnlink: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Input))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Input))
            .clickable(onClick = onOpen)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 相关性强度（score ≥0.9 高 / ≥0.7 中 / 其余 弱）
            val (label, color) = when {
                note.score >= 0.9f -> "高相关" to Accent
                note.score >= 0.7f -> "中相关" to Muted
                else -> "弱相关" to Faint
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                    .background(if (color == Accent) Accent06 else Paper2)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(label, fontSize = 9.5.sp, color = color)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "解除",
                fontSize = 10.sp,
                color = Faint,
                modifier = Modifier.clickable(onClick = onUnlink)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            note.title.ifBlank { note.content.take(20) },
            fontSize = 12.5.sp,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        note.reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 10.5.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
