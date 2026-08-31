package com.voiceink.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Ink2
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.SerifFamily
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 屏 05 洞察页：三 tile + 连续记录点阵 + 24h 时段分布 + 关键词云（§11.4） */
@Composable
fun InsightsScreen(vm: InsightsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 180.dp)
    ) {
        item { Header() }
        item { TilesRow(ui) }
        item { StreakCard(ui) }
        item { HoursCard(ui) }
        item { CloudCard(ui) }
    }
}

@Composable
private fun Header() {
    val fmt = SimpleDateFormat("M 月 d 日", Locale.getDefault())
    val now = System.currentTimeMillis()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                "洞察",
                fontFamily = SerifFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = Ink
            )
            Text(
                "${fmt.format(Date(now - 6 * 86_400_000L))} — ${fmt.format(Date(now))}",
                fontSize = 11.sp, color = Faint, letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(VoiceInkRadius.Chip))
                .border(1.dp, Line, RoundedCornerShape(VoiceInkRadius.Chip))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text("本周", fontSize = 10.5.sp, color = Muted)
        }
    }
}

@Composable
private fun TilesRow(ui: InsightsUiState) {
    val diff = ui.weekNotes - ui.lastWeekNotes
    val rate = if (ui.todosTotal > 0) ui.todosDone * 100 / ui.todosTotal else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Tile(
            label = "记录条数", value = "${ui.weekNotes}",
            footer = if (diff >= 0) "较上周 +$diff" else "较上周 $diff",
            footerAccent = true, modifier = Modifier.weight(1f)
        )
        Tile(
            label = "提炼待办", value = "${ui.todosTotal}", unit = "项",
            footer = "已完成 ${ui.todosDone}", modifier = Modifier.weight(1f)
        )
        Tile(
            label = "完成率", value = "$rate", unit = "%",
            footer = "已完成 ${ui.todosDone} / 共 ${ui.todosTotal}", modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Tile(
    label: String, value: String, unit: String = "",
    footer: String, footerAccent: Boolean = false, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(VoiceInkRadius.Input))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Input))
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Text(label, fontSize = 9.5.sp, letterSpacing = 1.sp, color = Faint, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Light, color = Ink, lineHeight = 28.sp)
            if (unit.isNotEmpty()) Text(unit, fontSize = 11.sp, color = Muted)
        }
        Text(
            footer,
            fontSize = 10.sp,
            color = if (footerAccent) Accent else Faint,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun StreakCard(ui: InsightsUiState) {
    SheetCard(title = "连续记录", note = "${ui.streak} 天") {
        Row(
            modifier = Modifier.padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ui.last14Days.forEach { has ->
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (has) Color(0x571A1A1A) else Color(0x211A1A1A))
                )
            }
            Spacer(Modifier.weight(1f))
            Text("最长连续 ${ui.longestStreak} 天", fontSize = 10.sp, color = Faint)
        }
    }
}

@Composable
private fun HoursCard(ui: InsightsUiState) {
    val max = ui.hourBuckets.maxOrNull()?.coerceAtLeast(1) ?: 1
    val note = if (ui.peakHour >= 0) "灵感高峰在 ${ui.peakHour}:00 前后" else "还没有足够数据"
    SheetCard(title = "记录时段分布", note = note) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ui.hourBuckets.forEachIndexed { hour, count ->
                val h = if (count == 0) 3 else (count * 88 / max).coerceAtLeast(3)
                val isPeak = hour == ui.peakHour && count > 0
                Box(
                    Modifier
                        .weight(1f)
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                        .background(
                            when {
                                isPeak -> Accent
                                hour >= 20 -> Color(0x2B1A1A1A)
                                else -> Color(0x1A1A1A1A)
                            }
                        )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "6", "12", "18", "23").forEach {
                Text(it, fontSize = 9.5.sp, color = Faint)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudCard(ui: InsightsUiState) {
    SheetCard(title = "灵感关键词", note = "近 30 天") {
        if (ui.topTags.isEmpty()) {
            Text(
                "还没有标签，记录几条笔记后由 AI 提炼",
                fontSize = 12.sp, color = Faint,
                modifier = Modifier.padding(top = 15.dp)
            )
        } else {
            FlowRow(
                modifier = Modifier.padding(top = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.topTags.forEachIndexed { i, (tag, _) ->
                    // 按名次映射设计稿 s1–s5 字号阶梯
                    val rank = i * 5 / ui.topTags.size.coerceAtLeast(1)
                    when (rank) {
                        0 -> Text(tag, fontFamily = SerifFamily, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                        1 -> Text(tag, fontFamily = SerifFamily, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        2 -> Text(tag, fontFamily = SerifFamily, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink2)
                        3 -> Text(tag, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Muted)
                        else -> Text(tag, fontSize = 12.5.sp, color = Faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCard(title: String, note: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Card))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(title, fontFamily = SerifFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(note, fontSize = 10.5.sp, color = Faint)
        }
        content()
    }
}
