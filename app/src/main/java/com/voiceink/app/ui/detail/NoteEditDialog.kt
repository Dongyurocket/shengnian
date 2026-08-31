package com.voiceink.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.voiceink.app.ai.diagram.DiagramKind
import com.voiceink.app.data.local.entity.NoteAttachmentEntity
import com.voiceink.app.data.local.entity.NoteDiagramEntity
import com.voiceink.app.data.local.entity.NoteEntity
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

@Composable
fun NoteEditDialog(
    note: NoteEntity,
    attachments: List<NoteAttachmentEntity>,
    canAddImage: Boolean = true,
    attachmentBusy: Boolean = false,
    attachmentError: String? = null,
    onAddImage: () -> Unit,
    onRemoveImage: (NoteAttachmentEntity) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onReorganize: (String, String) -> Unit
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(note.content) }
    val canSave = !attachmentBusy && (title.isNotBlank() || content.isNotBlank() || attachments.isNotEmpty())
    val canReorganize = !attachmentBusy && (content.isNotBlank() || attachments.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text("编辑笔记", style = VoiceInkTextStyles.NoteTitle)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("标题", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text("给这条笔记一个标题", fontSize = 13.sp, color = Faint) },
                    shape = RoundedCornerShape(VoiceInkRadius.Input),
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("正文", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    minLines = 5,
                    maxLines = 10,
                    placeholder = { Text("补充内容后，可以再次交给 AI 整理", fontSize = 13.sp, color = Faint) },
                    shape = RoundedCornerShape(VoiceInkRadius.Input),
                    colors = editFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 230.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("图片", fontSize = 11.sp, color = Muted)
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = canAddImage && !attachmentBusy, onClick = onAddImage) {
                        if (attachmentBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = if (canAddImage) Accent else Faint)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (attachmentBusy) "保存图片…" else "添加图片",
                            color = if (canAddImage && !attachmentBusy) Accent else Faint,
                            fontSize = 11.5.sp
                        )
                    }
                }
                attachmentError?.let { message ->
                    Text(message, color = Accent, fontSize = 10.5.sp, modifier = Modifier.padding(top = 5.dp))
                }
                AttachmentStrip(
                    attachments = attachments,
                    onRemove = onRemoveImage,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canReorganize,
                onClick = { onReorganize(title, content) }
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = if (canReorganize) Accent else Faint)
                Spacer(Modifier.width(4.dp))
                Text("保存并重新整理", color = if (canReorganize) Accent else Faint)
            }
        },
        dismissButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(title, content) }
            ) { Text("仅保存", color = if (canSave) Ink else Faint) }
        }
    )
}

@Composable
private fun editFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Paper2,
    unfocusedContainerColor = Paper2,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Accent,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink
)

@Composable
fun DiagramSection(
    diagrams: List<NoteDiagramEntity>,
    state: NoteDetailViewModel.DiagramUiState,
    onGenerate: (DiagramKind) -> Unit,
    onClearError: () -> Unit
) {
    Column(Modifier.padding(top = 18.dp)) {
        androidx.compose.material3.HorizontalDivider(color = com.voiceink.app.ui.theme.Line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("AI 图表", style = VoiceInkTextStyles.SectionLabel)
            Text("从笔记生成结构图", fontSize = 10.5.sp, color = Faint)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            TextButton(enabled = !state.loading, onClick = { onGenerate(DiagramKind.FLOWCHART) }) {
                Text("生成流程图", color = if (state.loading) Faint else Accent, fontSize = 11.5.sp)
            }
            TextButton(enabled = !state.loading, onClick = { onGenerate(DiagramKind.MINDMAP) }) {
                Text("生成思维导图", color = if (state.loading) Faint else Accent, fontSize = 11.5.sp)
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(start = 4.dp).height(18.dp), color = Accent, strokeWidth = 2.dp)
            }
        }
        state.error?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(message, color = Accent, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearError) { Text("知道了", color = Muted, fontSize = 10.5.sp) }
            }
        }
        diagrams.forEach { diagram -> DiagramCard(diagram) }
    }
}
