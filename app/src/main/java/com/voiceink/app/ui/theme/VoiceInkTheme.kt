package com.voiceink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VoiceInkColors = lightColorScheme(
    primary = Accent,
    onPrimary = SurfaceCard,
    background = Paper,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = Paper2,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line2
)

/** 圆角 token（§11.2）：chip 7 / 小件 10 / 输入 14 / 卡片 18 / 大容器 22 */
object VoiceInkRadius {
    val Chip = 7.dp
    val Small = 10.dp
    val Input = 14.dp
    val Card = 18.dp
    val Large = 22.dp
}

private val VoiceInkShapes = Shapes(
    small = RoundedCornerShape(VoiceInkRadius.Small),
    medium = RoundedCornerShape(VoiceInkRadius.Input),
    large = RoundedCornerShape(VoiceInkRadius.Card),
    extraLarge = RoundedCornerShape(VoiceInkRadius.Large)
)

@Composable
fun VoiceInkTheme(content: @Composable () -> Unit) {
    // 设计稿只有亮色基调；深色模式暂不单独设计
    MaterialTheme(
        colorScheme = VoiceInkColors,
        typography = VoiceInkTypography,
        shapes = VoiceInkShapes,
        content = content
    )
}
