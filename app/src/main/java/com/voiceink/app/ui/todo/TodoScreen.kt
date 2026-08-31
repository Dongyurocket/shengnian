package com.voiceink.app.ui.todo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceink.app.core.TimeUtils
import com.voiceink.app.data.local.entity.TodoEntity
import com.voiceink.app.data.local.entity.TodoReminderEntity
import com.voiceink.app.data.repo.TodoRepository
import com.voiceink.app.ui.theme.Accent
import com.voiceink.app.ui.theme.Accent12
import com.voiceink.app.ui.theme.Faint
import com.voiceink.app.ui.theme.Ink
import com.voiceink.app.ui.theme.Ink2
import com.voiceink.app.ui.theme.Line
import com.voiceink.app.ui.theme.Line2
import com.voiceink.app.ui.theme.Muted
import com.voiceink.app.ui.theme.Paper
import com.voiceink.app.ui.theme.Paper2
import com.voiceink.app.ui.theme.SurfaceCard
import com.voiceink.app.ui.theme.VoiceInkRadius
import com.voiceink.app.ui.theme.VoiceInkTextStyles

/**
 * 屏 04 智能待办页：顶部日期 + 完成进度条；「今天/接下来」分组卡片；
 * 优先级标、来源笔记回溯链接、圆形勾选完成、左滑删除、点按编辑时间。
 */
@Composable
fun TodoScreen(
    onOpenNote: (Long) -> Unit = {},
    vm: TodoViewModel = hiltViewModel()
) {
    val todos by vm.todos.collectAsStateWithLifecycle()
    val remindersByTodo by vm.remindersByTodo.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var pendingCalendarSave by remember { mutableStateOf<Pair<TodoEntity, TodoEditInput>?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingCalendarSave?.let { (todo, input) ->
            pendingCalendarSave = null
            vm.save(todo, input) { editing = null }
        }
    }

    fun saveTodo(todo: TodoEntity, input: TodoEditInput) {
        if (input.syncCalendar && !vm.hasCalendarPermission()) {
            pendingCalendarSave = todo to input
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        } else {
            vm.save(todo, input) { editing = null }
        }
    }

    // API 33+ 通知运行时权限
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val now = System.currentTimeMillis()
    val eod = TimeUtils.endOfToday(now)
    val today = todos.filter { it.deadline != null && it.deadline <= eod }
    val next = todos.filter { it.deadline != null && it.deadline > eod }
    val noDate = todos.filter { it.deadline == null }
    val todayDone = today.count { it.done }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Text(
            "待办",
            fontFamily = com.voiceink.app.ui.theme.SerifFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            color = Ink,
            modifier = Modifier.padding(top = 4.dp)
        )

        // 完成进度（设计稿 .prog）
        Column(Modifier.padding(top = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(TimeUtils.headerDate(now), fontSize = 11.sp, color = Muted)
                Text(
                    "今天 ${today.size} 项 · 已完成 $todayDone",
                    fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Line)
            ) {
                val progress = if (today.isEmpty()) 0f else todayDone.toFloat() / today.size
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Accent.copy(alpha = 0.8f))
                )
            }
        }

        // 精确闹钟权限引导（API 31+）
        if (!vm.canScheduleExact()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(VoiceInkRadius.Input))
                    .background(Accent12)
                    .clickable {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    "未授权精确闹钟，提醒可能不准点，点我去开启",
                    fontSize = 11.5.sp, color = Accent
                )
            }
        }

        message?.let { status ->
            Text(
                status,
                fontSize = 11.sp,
                color = if (status.contains("失败") || status.contains("未授权")) Accent else Muted,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 180.dp)
        ) {
            item { TodoGroup("今天", today, remindersByTodo, vm, onOpenNote, onEdit = { editing = it }) }
            item { TodoGroup("接下来", next, remindersByTodo, vm, onOpenNote, onEdit = { editing = it }) }
            item { TodoGroup("无时间", noDate, remindersByTodo, vm, onOpenNote, onEdit = { editing = it }) }

            if (todos.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp)
                    ) {
                        Text(
                            "念起成行",
                            fontFamily = com.voiceink.app.ui.theme.SerifFamily,
                            fontSize = 18.5.sp, color = Muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("说出一件事，AI 会自动提炼成待办", fontSize = 12.5.sp, color = Faint)
                    }
                }
            }
        }
    }

    editing?.let { todo ->
        TodoEditDialog(
            todo = todo,
            reminders = remindersByTodo[todo.id].orEmpty(),
            onDismiss = { editing = null },
            onSave = { input -> saveTodo(todo, input) }
        )
    }
}

@Composable
private fun TodoGroup(
    label: String,
    items: List<TodoEntity>,
    remindersByTodo: Map<Long, List<TodoReminderEntity>>,
    vm: TodoViewModel,
    onOpenNote: (Long) -> Unit,
    onEdit: (TodoEntity) -> Unit
) {
    if (items.isEmpty()) return
    val open = items.filter { !it.done }
    val done = items.filter { it.done }

    Text(
        label,
        style = VoiceInkTextStyles.SectionLabel,
        modifier = Modifier.padding(bottom = 9.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .clip(RoundedCornerShape(VoiceInkRadius.Card))
            .background(SurfaceCard)
            .border(1.dp, Color(0x0D1A1A1A), RoundedCornerShape(VoiceInkRadius.Card))
    ) {
        open.forEach { todo ->
            TodoRow(todo, remindersByTodo[todo.id].orEmpty(), vm, onOpenNote, onEdit)
        }
        if (done.isNotEmpty()) {
            Text(
                "已完成 ${done.size} 项",
                fontSize = 10.5.sp, color = Faint, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 2.dp)
            )
            done.forEach { todo ->
                TodoRow(todo, remindersByTodo[todo.id].orEmpty(), vm, onOpenNote, onEdit)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    todo: TodoEntity,
    reminders: List<TodoReminderEntity>,
    vm: TodoViewModel,
    onOpenNote: (Long) -> Unit,
    onEdit: (TodoEntity) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                vm.delete(todo)
            }
            value != SwipeToDismissBoxValue.EndToStart
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x0F1A1A1A)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("删除", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(end = 18.dp))
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .clickable { onEdit(todo) }
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 圆形勾选（设计稿 .box）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(if (todo.done) Accent else Color.Transparent)
                    .border(1.5.dp, if (todo.done) Accent else Faint, CircleShape)
                    .clickable { vm.toggleDone(todo) }
            ) {
                if (todo.done) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        todo.content,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = if (todo.done) Faint else Ink,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!todo.done) {
                        PriorityChip(todo.priority)
                    }
                }
                val hasSub = todo.deadline != null || todo.sourceNoteId != null ||
                    reminders.isNotEmpty() || todo.calendarEventId != null
                if (hasSub) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        todo.deadline?.let {
                            Text(
                                TimeUtils.dueLabel(it),
                                fontSize = 10.5.sp,
                                color = Muted,
                                letterSpacing = 0.3.sp
                            )
                        }
                        if (reminders.isNotEmpty()) {
                            if (todo.deadline != null) Spacer(Modifier.width(9.dp))
                            Text(
                                "提醒 ${reminders.size} 次",
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                        }
                        if (todo.calendarEventId != null) {
                            Spacer(Modifier.width(9.dp))
                            Text("已同步日历", fontSize = 10.5.sp, color = Faint)
                        }
                        todo.sourceNoteId?.let { noteId ->
                            if (todo.deadline != null) Spacer(Modifier.width(9.dp))
                            Text(
                                "来源笔记",
                                fontSize = 10.5.sp,
                                color = Faint,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { onOpenNote(noteId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: Int) {
    val (label, bg, fg) = when (priority) {
        2 -> Triple("高", Accent12, Accent)
        1 -> Triple("中", Paper2, Muted)
        else -> Triple("低", Paper2, Faint)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 7.dp)
    ) {
        Text(label, fontSize = 9.5.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** 编辑待办内容、截止时间和完整提醒时间表。 */
@Composable
private fun TodoEditDialog(
    todo: TodoEntity,
    reminders: List<com.voiceink.app.data.local.entity.TodoReminderEntity>,
    onDismiss: () -> Unit,
    onSave: (TodoEditInput) -> Unit
) {
    val initialDeadlineText = remember(todo.id) {
        todo.deadline?.let { TimeUtils.formatDateTime(it) } ?: ""
    }
    val initialTimesText = remember(todo.id) {
        reminders.joinToString("\n") { TimeUtils.formatDateTime(it.triggerAt) }
    }
    var content by remember(todo.id) { mutableStateOf(todo.content) }
    var deadlineText by remember(todo.id) { mutableStateOf(initialDeadlineText) }
    var countText by remember(todo.id) {
        mutableStateOf(todo.reminderCount.coerceIn(0, TodoRepository.MAX_REMINDERS).toString())
    }
    var intervalText by remember(todo.id) {
        mutableStateOf(todo.reminderIntervalMinutes.toString())
    }
    var timesText by remember(todo.id) { mutableStateOf(initialTimesText) }
    var syncCalendar by remember(todo.id) { mutableStateOf(todo.calendarEventId != null) }
    var error by remember(todo.id) { mutableStateOf<String?>(null) }

    fun generateTimes() {
        val count = countText.toIntOrNull()?.coerceIn(0, TodoRepository.MAX_REMINDERS) ?: 1
        val interval = intervalText.toIntOrNull()?.coerceIn(1, TodoRepository.MAX_INTERVAL_MINUTES) ?: 10
        val first = timesText.lines()
            .mapNotNull { TimeUtils.parseDateTime(it.trim()) }
            .firstOrNull()
            ?: deadlineText.trim().takeIf { it.isNotBlank() }?.let { TimeUtils.parseDateTime(it) }
                ?.let { it - todo.remindLeadMinutes * 60_000L }
        if (first == null) {
            error = "请先填写一个有效的截止时间或提醒时间"
            return
        }
        timesText = reminderTimesFrom(first, count, interval)
            .joinToString("\n") { TimeUtils.formatDateTime(it) }
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("编辑待办", style = VoiceInkTextStyles.NoteTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("待办内容", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it; error = null },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(VoiceInkRadius.Input),
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("截止时间（yyyy-MM-dd HH:mm，留空为无）", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it; error = null },
                    singleLine = true,
                    isError = deadlineText.isNotBlank() && TimeUtils.parseDateTime(deadlineText.trim()) == null,
                    shape = RoundedCornerShape(VoiceInkRadius.Input),
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "提醒次数",
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "间隔（分钟）",
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("每次提醒的具体时间（每行一条）", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = timesText,
                    onValueChange = { timesText = it; error = null },
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("2026-09-01 07:50\n2026-09-01 08:00", fontSize = 12.sp, color = Faint) },
                    shape = RoundedCornerShape(VoiceInkRadius.Input),
                    colors = editFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = ::generateTimes) {
                    Text("按次数和间隔生成时间", color = Accent, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("同步到手机日历", fontSize = 13.sp, color = Ink)
                        Text("创建或更新一条日历事件", fontSize = 10.5.sp, color = Faint)
                    }
                    Switch(
                        checked = syncCalendar,
                        onCheckedChange = { syncCalendar = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                    )
                }
                error?.let { Text(it, fontSize = 10.5.sp, color = Accent, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalizedContent = content.trim()
                val deadline = deadlineText.trim().takeIf { it.isNotBlank() }
                    ?.let { TimeUtils.parseDateTime(it) }
                val times = timesText.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { TimeUtils.parseDateTime(it) }
                when {
                    normalizedContent.isBlank() -> error = "待办内容不能为空"
                    deadlineText.isNotBlank() && deadline == null -> error = "截止时间格式不正确"
                    times.any { it == null } -> error = "提醒时间格式不正确"
                    else -> {
                        val parsedTimes = times.filterNotNull()
                        val requestedCount = countText.toIntOrNull()
                            ?.coerceIn(0, TodoRepository.MAX_REMINDERS)
                            ?: parsedTimes.size
                        val interval = intervalText.toIntOrNull()
                            ?.coerceIn(1, TodoRepository.MAX_INTERVAL_MINUTES) ?: 10
                        val timesWereUntouched = timesText == initialTimesText
                        val scheduleSettingsChanged =
                            deadlineText.trim() != initialDeadlineText.trim() ||
                                requestedCount != todo.reminderCount.coerceIn(0, TodoRepository.MAX_REMINDERS) ||
                                interval != todo.reminderIntervalMinutes
                        val generated = if (timesWereUntouched && scheduleSettingsChanged) {
                            val first = deadline?.let {
                                it - todo.remindLeadMinutes * 60_000L
                            } ?: parsedTimes.firstOrNull()
                            first?.let { reminderTimesFrom(it, requestedCount, interval) }.orEmpty()
                        } else if (parsedTimes.isEmpty() && deadline != null) {
                            val first = deadline - todo.remindLeadMinutes * 60_000L
                            reminderTimesFrom(first, requestedCount, interval)
                        } else parsedTimes
                        onSave(
                            TodoEditInput(
                                content = normalizedContent,
                                deadline = deadline,
                                reminders = generated,
                                reminderCount = requestedCount,
                                reminderIntervalMinutes = interval,
                                syncCalendar = syncCalendar
                            )
                        )
                    }
                }
            }) { Text("保存", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
        }
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(VoiceInkRadius.Input),
            colors = editFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun editFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Paper2,
    unfocusedContainerColor = Paper2,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Accent
)
