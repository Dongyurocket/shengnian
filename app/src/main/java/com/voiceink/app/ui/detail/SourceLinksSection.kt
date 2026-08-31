package com.voiceink.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.data.local.entity.NoteSourceEntity
import com.voiceink.app.data.local.entity.NoteSourceStatus
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius

@Composable
fun SourceLinksSection(
    sources: List<NoteSourceEntity>,
    onOpen: (String) -> Unit
) {
    if (sources.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        androidx.compose.material3.HorizontalDivider(color = Line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("来源链接", fontSize = 10.5.sp, color = Faint, letterSpacing = 1.7.sp)
            Text("${sources.size} 条", fontSize = 10.5.sp, color = Faint)
        }
        sources.forEach { source ->
            SourceRow(source, onOpen)
        }
    }
}

@Composable
private fun SourceRow(source: NoteSourceEntity, onOpen: (String) -> Unit) {
    val ready = source.status == NoteSourceStatus.READY
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Input))
            .background(if (ready) SurfaceCard else Paper2)
            .border(1.dp, if (ready) Color(0x0D1A1A1A) else Line, RoundedCornerShape(VoiceInkRadius.Input))
            .clickable { onOpen(source.url) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = if (ready) Accent else Faint)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source.title.ifBlank { source.url },
                fontSize = 12.5.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (source.status) {
                    NoteSourceStatus.READY -> source.excerpt
                    NoteSourceStatus.PENDING -> "正在提取页面内容…"
                    NoteSourceStatus.UNSUPPORTED -> source.error ?: "暂不支持此内容类型"
                    else -> source.error ?: "提取失败，保留原始链接"
                },
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = if (ready) Muted else Faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (ready) {
                Text("打开链接", fontSize = 10.sp, color = Accent, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}
