package com.voiceink.app.ui.detail

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.ai.diagram.DiagramKind
import com.voiceink.app.ai.diagram.DiagramNode
import com.voiceink.app.ai.diagram.DiagramSpec
import com.voiceink.app.data.local.entity.NoteDiagramEntity
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius

@Composable
fun DiagramCard(diagram: NoteDiagramEntity) {
    val spec = DiagramSpec.fromJson(diagram.specJson) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard, RoundedCornerShape(VoiceInkRadius.Card))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text("${spec.kind.label} · ${spec.title}", fontSize = 12.5.sp, color = Ink)
        DiagramCanvas(spec)
    }
}

@Composable
private fun DiagramCanvas(spec: DiagramSpec) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val columns = if (spec.kind == DiagramKind.FLOWCHART || maxWidth < 360.dp) {
            1
        } else {
            minOf(2, spec.nodes.size.coerceAtLeast(1))
        }
        val rows = (spec.nodes.size + columns - 1) / columns
        val contentHeight = rows * 48 + (rows - 1).coerceAtLeast(0) * 30 + 24
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight.coerceAtLeast(210).dp)
                .padding(top = 10.dp)
        ) {
            drawDiagram(spec)
        }
    }
}

private data class NodeRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

private fun DrawScope.drawDiagram(spec: DiagramSpec) {
    val gap = 14.dp.toPx()
    val columns = when {
        spec.kind == DiagramKind.FLOWCHART -> 1
        size.width < 360.dp.toPx() -> 1
        else -> minOf(2, spec.nodes.size.coerceAtLeast(1))
    }
    val availableWidth = (size.width - (columns - 1) * gap).coerceAtLeast(1f)
    val nodeWidth = minOf(148.dp.toPx(), availableWidth / columns)
    val nodeHeight = 48.dp.toPx()
    val verticalGap = 30.dp.toPx()
    val rowCount = (spec.nodes.size + columns - 1) / columns
    val totalWidth = columns * nodeWidth + (columns - 1) * gap
    val startX = ((size.width - totalWidth) / 2f).coerceAtLeast(0f)
    val totalHeight = rowCount * nodeHeight + (rowCount - 1).coerceAtLeast(0) * verticalGap
    val startY = ((size.height - totalHeight) / 2f).coerceAtLeast(0f)
    val rects = linkedMapOf<String, NodeRect>()

    spec.nodes.forEachIndexed { index, node ->
        val column = index % columns
        val row = index / columns
        val left = startX + column * (nodeWidth + gap)
        val top = startY + row * (nodeHeight + verticalGap)
        rects[node.id] = NodeRect(left, top, left + nodeWidth, top + nodeHeight)
    }

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Line.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1.5.dp.toPx()
    }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Accent.toArgb()
        style = Paint.Style.FILL
    }
    drawIntoCanvas { canvas ->
        spec.edges.forEach { edge ->
            val from = rects[edge.from] ?: return@forEach
            val to = rects[edge.to] ?: return@forEach
            val horizontal = kotlin.math.abs(to.centerX - from.centerX) >
                kotlin.math.abs(to.centerY - from.centerY)
            val startXLine: Float
            val startYLine: Float
            val endXLine: Float
            val endYLine: Float
            if (horizontal) {
                val rightward = to.centerX >= from.centerX
                startXLine = if (rightward) from.right else from.left
                endXLine = if (rightward) to.left else to.right
                startYLine = from.centerY
                endYLine = to.centerY
            } else {
                val downward = to.centerY >= from.centerY
                startXLine = from.centerX
                endXLine = to.centerX
                startYLine = if (downward) from.bottom else from.top
                endYLine = if (downward) to.top else to.bottom
            }
            canvas.nativeCanvas.drawLine(startXLine, startYLine, endXLine, endYLine, linePaint)
            drawArrow(
                canvas.nativeCanvas,
                endXLine,
                endYLine,
                if (horizontal) {
                    if (endXLine >= startXLine) 1f else -1f
                } else {
                    if (endYLine >= startYLine) 1f else -1f
                },
                horizontal,
                8.dp.toPx(),
                5.dp.toPx(),
                arrowPaint
            )
        }
        spec.nodes.forEach { node ->
            val rect = rects[node.id] ?: return@forEach
            val isRoot = node.shape == "root"
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isRoot) Accent06.toArgb() else Paper.toArgb()
                style = Paint.Style.FILL
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isRoot) Accent.toArgb() else Line.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 1.2.dp.toPx()
            }
            val radius = if (node.shape == "decision") 4.dp.toPx() else 10.dp.toPx()
            canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, radius, radius, fillPaint
            )
            canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, radius, radius, strokePaint
            )
            drawNodeLabel(
                canvas.nativeCanvas,
                node,
                rect,
                textSize = 12.sp.toPx(),
                horizontalPadding = 16.dp.toPx()
            )
        }
    }
}

private fun drawArrow(
    canvas: android.graphics.Canvas,
    x: Float,
    y: Float,
    direction: Float,
    horizontal: Boolean,
    length: Float,
    half: Float,
    paint: Paint
) {
    val path = Path().apply {
        if (horizontal) {
            moveTo(x, y)
            lineTo(x - direction * length, y - half)
            lineTo(x - direction * length, y + half)
        } else {
            moveTo(x, y)
            lineTo(x - half, y - direction * length)
            lineTo(x + half, y - direction * length)
        }
        close()
    }
    canvas.drawPath(path, paint)
}

private fun drawNodeLabel(
    canvas: android.graphics.Canvas,
    node: DiagramNode,
    rect: NodeRect,
    textSize: Float,
    horizontalPadding: Float
) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ink.toArgb()
        this.textSize = textSize
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    val maxWidth = (rect.right - rect.left - horizontalPadding).coerceAtLeast(30f)
    val label = TextUtils.ellipsize(
        node.label,
        paint,
        maxWidth,
        TextUtils.TruncateAt.END
    ).toString()
    val baseline = rect.centerY - (paint.ascent() + paint.descent()) / 2f
    canvas.drawText(label, rect.centerX, baseline, paint)
}
