package com.voiceink.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent06
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 屏 02 快速记录页：进入即弹键盘（语音转写交给系统输入法），
 * 保存后立刻可输入下一条；AI 全部异步（§6）。
 * @param mode "todo" 时按待办优先（桌面快捷方式入口）
 */
@Composable
fun CaptureScreen(
    onDone: () -> Unit,
    mode: String? = null,
    vm: CaptureViewModel = hiltViewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val todoMode = mode == "todo"

    // 进入即弹键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // 保存成功短暂反馈
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(vm.savedCount) {
        if (vm.savedCount > 0) {
            showSaved = true
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .imePadding()
    ) {
        // 顶部导航：取消 / 完成（设计稿 .nav）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "取消",
                fontSize = 15.5.sp,
                color = Muted,
                modifier = Modifier
                    .clickable(onClick = onDone)
                    .padding(vertical = 6.dp)
            )
            Text(
                "完成",
                fontSize = 15.5.sp,
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { vm.saveAndContinue(if (todoMode) "todo" else null) }
                    .padding(vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = "今天 ${TimeUtils.timeOfDay(System.currentTimeMillis())} · " +
                    if (todoMode) "待办优先" else "未整理",
                fontSize = 10.5.sp,
                color = Faint,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // 衬线编辑区（语音输入由用户输入法完成）
            BasicTextField(
                value = vm.text,
                onValueChange = vm::onTextChange,
                textStyle = VoiceInkTextStyles.EditorBody,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isCtrlPressed) {
                            vm.saveAndContinue(if (todoMode) "todo" else null)
                            true
                        } else false
                    },
                decorationBox = { inner ->
                    if (vm.text.isEmpty()) {
                        Text(
                            text = if (todoMode) "记一件待办…" else "说点什么，或写点什么…",
                            style = VoiceInkTextStyles.EditorBody.copy(color = Faint)
                        )
                    }
                    inner()
                }
            )

            // AI 提示条（常驻）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(VoiceInkRadius.Input))
                    .background(Accent06)
                    .border(1.dp, Accent12, RoundedCornerShape(VoiceInkRadius.Input))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (showSaved) "已保存，AI 整理中…" else "保存后自动整理，并提炼待办",
                    fontSize = 11.5.sp,
                    color = Accent,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

