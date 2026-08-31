package com.voiceink.app.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Paper2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AttachmentStrip(
    attachments: List<NoteAttachmentEntity>,
    onRemove: ((NoteAttachmentEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentThumb(attachment, onRemove)
        }
    }
}

@Composable
private fun AttachmentThumb(
    attachment: NoteAttachmentEntity,
    onRemove: ((NoteAttachmentEntity) -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(VoiceInkThumbRadius))
            .background(Paper2)
            .border(1.dp, Line, RoundedCornerShape(VoiceInkThumbRadius))
    ) {
        var bitmap by remember(attachment.localPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(attachment.localPath) {
            bitmap = withContext(Dispatchers.IO) {
                decodeFileThumbnail(attachment.localPath)
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            androidx.compose.material3.Text(
                if (File(attachment.localPath).isFile) "图片" else "图片不可用",
                color = Faint,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (onRemove != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xCCFFFFFF))
                    .clickable { onRemove(attachment) }
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除图片",
                    tint = Accent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private val VoiceInkThumbRadius = 10.dp
