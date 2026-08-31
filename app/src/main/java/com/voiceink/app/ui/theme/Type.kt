package com.voiceink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体约定（§11.2）：
 * - 标题 / 编辑器正文 = 衬线（系统 Noto Serif CJK 回退）
 * - 界面 / 正文 = 无衬线
 * - 数据与时间 = 无衬线 300 + tabular-nums（Compose 用 FontFeatureSetting）
 */
val SerifFamily = FontFamily.Serif
val SansFamily = FontFamily.Default

/** 设计稿专用文本样式（M3 Typography 之外的补充） */
object VoiceInkTextStyles {
    val BrandName = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp, lineHeight = 30.sp, letterSpacing = 3.8.sp
    )
    val BrandSlogan = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 2.sp, color = Faint
    )
    val NoteTitle = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.16.sp, color = Ink
    )
    val NoteSummary = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 21.5.sp, color = Muted
    )
    val SectionLabel = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp, letterSpacing = 1.7.sp, color = Faint
    )
    val Meta = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Light,
        fontSize = 10.5.sp, letterSpacing = 0.4.sp, color = Faint
    )
    val Chip = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp, letterSpacing = 0.3.sp
    )
    val EditorBody = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Normal,
        fontSize = 18.5.sp, lineHeight = 30.sp, color = Ink
    )
}

val VoiceInkTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.5.sp, lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 10.5.sp, lineHeight = 14.sp
    )
)
