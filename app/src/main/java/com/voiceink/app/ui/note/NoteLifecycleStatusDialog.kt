package com.voiceink.app.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceink.app.data.local.entity.NoteLifecycleStatus
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

@Composable
fun NoteLifecycleStatusDialog(
    current: NoteLifecycleStatus,
    onDismiss: () -> Unit,
    onSave: (NoteLifecycleStatus) -> Unit
) {
    var selected by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("标记笔记状态", style = VoiceInkTextStyles.NoteTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                NoteLifecycleStatus.entries.forEach { status ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected == status) Accent06 else Paper2,
                                RoundedCornerShape(VoiceInkRadius.Input)
                            )
                            .clickable { selected = status }
                            .padding(horizontal = 13.dp, vertical = 11.dp)
                    ) {
                        Box(
                            Modifier
                                .background(
                                    if (selected == status) Accent else Muted,
                                    RoundedCornerShape(99.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                status.label,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (selected == status) "已选择" else "",
                            fontSize = 10.5.sp,
                            color = if (selected == status) Accent else Muted
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text("保存", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Ink) }
        }
    )
}
