package com.voiceink.app.ui.detail

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Paper2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PendingAttachmentStrip(
    uris: List<Uri>,
    onRemove: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uris.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uris.forEach { uri -> PendingAttachmentThumb(uri, onRemove) }
    }
}

@Composable
private fun PendingAttachmentThumb(uri: Uri, onRemove: (Uri) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                decodeUriThumbnail(context.contentResolver, uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Paper2)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "待上传图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } ?: Text("图片", color = Faint, fontSize = 10.5.sp, modifier = Modifier.align(Alignment.Center))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Color(0xCCFFFFFF))
                .clickable { onRemove(uri) }
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "移除图片", tint = Accent, modifier = Modifier.size(12.dp))
        }
    }
}
