package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.UsageTotals
import me.rerere.rikkahub.data.pulse.calculatePulseCost
import me.rerere.rikkahub.data.pulse.compactPulseNumber
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.model.DEFAULT_PULSE_PROMPT
import me.rerere.rikkahub.service.pulse.PulseRuntime
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun pulseTime(time: Long): String = if (time <= 0) "—" else
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

@Composable
fun PulseSettingsPage(initialTab: Int = 0, logs: Boolean = false) {
    val store = koinInject<SettingsStore>()
    val runtime = koinInject<PulseRuntime>()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val config = settings.pulse
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var deleting by remember { mutableStateOf<PulseTask?>(null) }
    var addWatcher by remember { mutableStateOf(false) }
    var editingWatcher by remember { mutableStateOf<PulseWatcher?>(null) }
    var deletingWatcher by remember { mutableStateOf<PulseWatcher?>(null) }
    var expandedActivity by remember { mutableStateOf<PulseActivityEntry?>(null) }
    var confirmTool by remember { mutableStateOf<String?>(null) }
    val workspaceRepository = koinInject<WorkspaceRepository>()
    val workspaces by workspaceRepository.listFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val pulseWorkspace = remember(workspaces, config.workspaceId) { workspaces.firstOrNull { it.id == config.workspaceId?.toString() && it.owner == "pulse" } }
    val workspaceSetup = rememberPulseWorkspaceSetup(config)
    workspaceSetup.overlays()
    val usageDao = koinInject<UsageRecordDAO>()
    var dayStart by remember { mutableLongStateOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (today != dayStart) dayStart = today
        }
    }
    val usage by remember(config.conversationId, dayStart, config.priceInputPerMillion, config.priceOutputPerMillion,
        config.priceCachedPerMillion, config.priceMultiplier) {
        config.conversationId?.let { id -> usageDao.observeConversationTotals(id.toString(), dayStart).map { calculatePulseCost(it, config) } }
            ?: flowOf(calculatePulseCost(UsageTotals(), config))
    }.collectAsStateWithLifecycle(initialValue = calculatePulseCost(UsageTotals(), config))
    fun update(transform: (PulseConfig) -> PulseConfig) {
        scope.launch { store.update { it.copy(pulse = transform(it.pulse)) } }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (logs) R.string.pulse_logs else R.string.pulse_settings_title)) },
            navigationIcon = { BackButton() })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!logs) PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                listOf(R.string.pulse_settings_basic, R.string.pulse_settings_heartbeat,
                    R.string.pulse_settings_tasks, R.string.pulse_settings_tools).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (logs) {
                    item { Text(stringResource(R.string.pulse_recent_activity), style = MaterialTheme.typography.titleMedium) }
                    if (config.activityLog.isEmpty()) item { Text(stringResource(R.string.pulse_log_empty)) }
                    items(config.activityLog.take(20), key = { it.id }) { entry ->
                        CardGroup {
                            item(headlineContent = { Text("${pulseTime(entry.at)} · ${pulseActivityType(entry.kind)} · ${pulseResultLabel(entry.result)}") },
                                supportingContent = { if (entry.summary.isNotBlank()) Text(entry.summary) })
                            if (entry.reportPath.isNotBlank() && pulseWorkspace != null) item(
                                onClick = { nav.navigate(Screen.WorkspaceFileEditor(pulseWorkspace.id, me.rerere.workspace.WorkspaceStorageArea.FILES.name, entry.reportPath)) },
                                headlineContent = { Text(stringResource(R.string.pulse_open_report)) },
                            )
                        }
                    }
                    item { Text(stringResource(R.string.pulse_run_log_title), style = MaterialTheme.typography.titleMedium) }
                    if (config.runLogs.isEmpty()) item { Text(stringResource(R.string.pulse_log_empty)) }
                    items(config.runLogs, key = { "${it.at}-${it.refId}-${it.kind}" }) { log ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                PulseLog(log.at, log.result, "${pulseActivityType(log.kind)} · ${log.durationMs} ms · ${log.promptTokens} / ${log.completionTokens} tokens${if (log.note.isNotBlank()) "\n${log.note}" else ""}")
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString("${pulseTime(log.at)} · ${log.kind} · ${log.result} · ${log.durationMs}ms · ${log.promptTokens}/${log.completionTokens} tokens${if (log.note.isBlank()) "" else "\n${log.note}"}"))
                            }) {
                                Icon(me.rerere.hugeicons.HugeIcons.Copy01, contentDescription = stringResource(R.string.copy))
                            }
                        }
                    }
                } else when (tab) {
                    0 -> {
                        item { CardGroup {
                            switchItem(checked = config.enableMemory, onCheckedChange = { value -> update { it.copy(enableMemory = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_memory_enabled)) })
                            item(onClick = { nav.navigate(Screen.PulseBackgroundSettings) },
                                headlineContent = { Text(stringResource(R.string.pulse_background_title)) })
                        } }
                        item { PulsePrompt(config.systemPrompt.ifBlank { DEFAULT_PULSE_PROMPT }, R.string.pulse_prompt_persona,
                            onChange = { text -> update { it.copy(systemPrompt = text) } },
                            restore = { update { it.copy(systemPrompt = DEFAULT_PULSE_PROMPT) } }) }
                    }
                    1 -> {
                        item { CardGroup {
                            switchItem(checked = config.enableHeartbeat, onCheckedChange = { value ->
                                update { it.copy(enableHeartbeat = value, lastHeartbeatAt = if (value) System.currentTimeMillis() else it.lastHeartbeatAt) }
                            }, headlineContent = { Text(stringResource(R.string.pulse_heartbeat_enabled)) })
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_interval_minutes)) }) {
                                HeartbeatIntervalInput(config.heartbeatIntervalMinutes) { minutes ->
                                    update { it.copy(heartbeatIntervalMinutes = minutes) }
                                }
                            }
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_active_hours)) }) {
                                val hours = "%02d:00–%02d:00".format(config.activeHoursStart, config.activeHoursEnd)
                                Text(when {
                                    config.activeHoursStart == config.activeHoursEnd -> stringResource(R.string.pulse_all_day)
                                    config.activeHoursStart > config.activeHoursEnd -> stringResource(R.string.pulse_active_hours_cross_midnight,
                                        "%02d:00".format(config.activeHoursStart), "%02d:00".format(config.activeHoursEnd))
                                    else -> hours
                                })
                                Text(stringResource(R.string.pulse_start_hour))
                                Slider(config.activeHoursStart.toFloat(), { value -> update { it.copy(activeHoursStart = value.roundToInt()) } },
                                    valueRange = 0f..23f, steps = 22)
                                Text(stringResource(R.string.pulse_end_hour))
                                Slider(config.activeHoursEnd.toFloat(), { value -> update { it.copy(activeHoursEnd = value.roundToInt()) } },
                                    valueRange = 0f..23f, steps = 22)
                            }
                            switchItem(checked = config.heartbeatSilentWhenNoContent,
                                onCheckedChange = { value -> update { it.copy(heartbeatSilentWhenNoContent = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_silent_when_ok)) })
                        } }
                        item { PulsePrompt(config.heartbeatPrompt.ifBlank { DEFAULT_HEARTBEAT_PROMPT }, R.string.pulse_prompt_heartbeat,
                            onChange = { text -> update { it.copy(heartbeatPrompt = text) } },
                            restore = { update { it.copy(heartbeatPrompt = DEFAULT_HEARTBEAT_PROMPT) } }) }
                        item { Button(onClick = { runtime.trigger(manual = true) }, enabled = config.enableHeartbeat) {
                            Text(stringResource(R.string.pulse_run_now))
                        } }
                        items(config.heartbeatLog) { log -> PulseLog(log.at, log.result, log.summary) }
                        item { Text(stringResource(R.string.pulse_recent_activity), style = MaterialTheme.typography.titleMedium) }
                        items(config.activityLog.take(20), key = { it.id }) { entry ->
                            ListItem(
                                modifier = Modifier.clickable { expandedActivity = entry },
                                headlineContent = { Text("${pulseTime(entry.at)} · ${pulseActivityType(entry.kind)} · ${pulseResultLabel(entry.result)}") },
                                supportingContent = { if (entry.summary.isNotBlank()) Text(entry.summary) },
                            )
                        }
                    }
                    2 -> {
                        item { CardGroup {
                            switchItem(checked = config.enableScheduledTasks,
                                onCheckedChange = { value -> update { it.copy(enableScheduledTasks = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_tasks_enabled)) })
                        } }
                        if (config.tasks.isEmpty()) item { Text(stringResource(R.string.pulse_task_empty)) }
                        items(config.tasks, key = { it.id }) { task ->
                            CardGroup {
                                switchItem(checked = task.enabled, onCheckedChange = { enabled ->
                                    update { pulse -> pulse.copy(tasks = pulse.tasks.map { if (it.id == task.id) it.copy(enabled = enabled) else it }) }
                                }, headlineContent = { Text(task.title) }, supportingContent = {
                                    val kind = when (task.kind) {
                                        PulseTaskKind.ONCE -> stringResource(R.string.pulse_task_once) + " · " + pulseTime(task.dueAt)
                                        PulseTaskKind.CRON -> stringResource(R.string.pulse_task_recurring) + " · " + task.cron + " · " + pulseTime(task.nextAt)
                                        PulseTaskKind.ON_HEARTBEAT -> stringResource(R.string.pulse_task_on_heartbeat)
                                    }
                                    Text(kind + if (task.lastRunAt > 0) "\n${pulseTime(task.lastRunAt)} · ${pulseResultLabel(task.lastResult)}" else "")
                                })
                                item(onClick = { deleting = task }, headlineContent = { Text(stringResource(R.string.pulse_delete_task), color = MaterialTheme.colorScheme.error) })
                            }
                        }
                    }
                    3 -> {
                        item { CardGroup {
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_model)) }) {
                                ModelSelector(config.chatModelId ?: settings.chatModelId, settings.providers, ModelType.CHAT) { model ->
                                    update { it.copy(chatModelId = model.id) }
                                }
                            }
                        } }
                        item { CardGroup(title = { Text(stringResource(R.string.pulse_cost_prices)) }) {
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_price_input)) }) {
                                PulsePriceInput(config.priceInputPerMillion) { value -> update { it.copy(priceInputPerMillion = value) } }
                            }
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_price_output)) }) {
                                PulsePriceInput(config.priceOutputPerMillion) { value -> update { it.copy(priceOutputPerMillion = value) } }
                            }
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_price_cached)) }) {
                                PulsePriceInput(config.priceCachedPerMillion) { value -> update { it.copy(priceCachedPerMillion = value) } }
                            }
                            formItem(headlineContent = { Text(stringResource(R.string.pulse_price_multiplier)) }) {
                                PulsePriceInput(config.priceMultiplier) { value -> update { it.copy(priceMultiplier = value) } }
                            }
                        } }
                        item { Text(pulseUsageLine(usage.inputTokens + usage.outputTokens, usage.costYuan), style = MaterialTheme.typography.titleSmall) }
                        if (pulseWorkspace == null) item { workspaceSetup.header() }
                        item { CardGroup(title = { Text(stringResource(R.string.pulse_workspace_tools)) }) {
                            if (pulseWorkspace == null) {
                                item(headlineContent = { Text(stringResource(R.string.pulse_workspace_tools_unavailable)) })
                            } else {
                                item(headlineContent = { Text(pulseWorkspace.name) }, supportingContent = {
                                    Text(stringResource(if (pulseWorkspace.shellStatus == "READY") R.string.pulse_workspace_ready else R.string.pulse_workspace_not_ready))
                                })
                                item(onClick = {
                                    scope.launch {
                                        runCatching { provisionPulseTemplates(workspaceRepository, pulseWorkspace.id) }
                                            .onSuccess { count ->
                                                update { it.copy(workspaceInitialized = true) }
                                                android.widget.Toast.makeText(context,
                                                    context.getString(R.string.pulse_templates_restored, count), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure { error -> android.widget.Toast.makeText(context,
                                                error.message ?: context.getString(R.string.pulse_templates_failed), android.widget.Toast.LENGTH_LONG).show() }
                                    }
                                }, headlineContent = { Text(stringResource(R.string.pulse_restore_templates)) })
                                item(onClick = { nav.navigate(Screen.Workspaces("pulse")) }, headlineContent = {
                                    Text(stringResource(R.string.pulse_workspace_manage))
                                })
                            }
                        } }
                        item { CardGroup {
                            switchItem(checked = config.enableInformation, onCheckedChange = { value -> update { it.copy(enableInformation = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_tool_info)) })
                            switchItem(checked = config.enableMemory, onCheckedChange = { value -> update { it.copy(enableMemory = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_tool_memory)) })
                            switchItem(checked = config.enableCalendarCreate, onCheckedChange = { value -> update { it.copy(enableCalendarCreate = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_tool_calendar)) })
                            switchItem(checked = config.notifyOnReport, onCheckedChange = { value -> update { it.copy(notifyOnReport = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_notify_reports)) })
                        } }
                        item { CardGroup(title = { Text(stringResource(R.string.pulse_tool_settings)) }) {
                            if (pulseWorkspace == null || pulseWorkspace.shellStatus != "READY") {
                                item(headlineContent = { Text(stringResource(R.string.pulse_workspace_tools_unavailable)) })
                            } else {
                                switchItem(checked = config.enableFileWrite, onCheckedChange = { value ->
                                    if (value) confirmTool = "files" else update { it.copy(enableFileWrite = false) }
                                }, headlineContent = { Text(stringResource(R.string.pulse_tool_file_write)) })
                                switchItem(checked = config.enableShell, onCheckedChange = { value ->
                                    if (value) confirmTool = "shell" else update { it.copy(enableShell = false) }
                                }, headlineContent = { Text(stringResource(R.string.pulse_tool_shell)) })
                            }
                            item(headlineContent = { Text(stringResource(R.string.pulse_tool_mcp_hidden)) })
                        } }
                        item { CardGroup {
                            switchItem(checked = config.enableWatchers, onCheckedChange = { value -> update { it.copy(enableWatchers = value) } },
                                headlineContent = { Text(stringResource(R.string.pulse_watchers_enabled)) })
                            config.watchers.forEach { watcher ->
                                val needsWorkspace = watcher.target == WatcherTarget.COMMAND && pulseWorkspace?.shellStatus != "READY"
                                item(onClick = if (needsWorkspace) null else ({
                                    update { p -> p.copy(watchers = p.watchers.map { if (it.id == watcher.id) it.copy(enabled = !it.enabled) else it }) }
                                }),
                                    headlineContent = { Text(
                                        watcher.name.ifBlank { stringResource(R.string.pulse_watcher_unnamed) },
                                        color = if (needsWorkspace) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else MaterialTheme.colorScheme.onSurface,
                                    ) },
                                    supportingContent = {
                                        Text(
                                            "${pulseWatcherType(watcher.target)} · ${watcher.everyMinutes.coerceIn(10, 240)} ${stringResource(R.string.pulse_minutes)} · ${if (needsWorkspace) stringResource(R.string.pulse_watcher_command_needs_workspace) else pulseResultLabel(watcher.lastResult)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    })
                                item(onClick = { editingWatcher = watcher }, headlineContent = { Text(stringResource(R.string.pulse_edit_watcher)) })
                                item(onClick = { deletingWatcher = watcher }, headlineContent = { Text(stringResource(R.string.pulse_delete_watcher), color = MaterialTheme.colorScheme.error) })
                            }
                            if (config.watchers.size < 20) item(onClick = { addWatcher = true }, headlineContent = { Text(stringResource(R.string.pulse_add_watcher)) })
                            else item(headlineContent = { Text(stringResource(R.string.pulse_watcher_limit)) })
                        } }
                        item { TextButton(onClick = { nav.navigate(Screen.PulseSettings(logs = true)) }) { Text(stringResource(R.string.pulse_view_logs)) } }
                    }
                }
            }
        }
    }
    confirmTool?.let { tool ->
        val title = stringResource(if (tool == "files") R.string.pulse_tool_file_write else R.string.pulse_tool_shell)
        AlertDialog(
            onDismissRequest = { confirmTool = null },
            title = { Text(title) },
            text = { Text(stringResource(if (tool == "files") R.string.pulse_tool_files_warning else R.string.pulse_tool_shell_warning)) },
            confirmButton = { TextButton(onClick = {
                val current = pulseWorkspace
                if (current != null && current.shellStatus == "READY") update {
                    if (tool == "files") it.copy(enableFileWrite = true) else it.copy(enableShell = true)
                }
                confirmTool = null
            }) { Text(stringResource(R.string.common_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmTool = null }) { Text(stringResource(R.string.chat_page_cancel)) } },
        )
    }
    deletingWatcher?.let { watcher -> AlertDialog(
        onDismissRequest = { deletingWatcher = null },
        title = { Text(stringResource(R.string.pulse_delete_watcher)) },
        text = { Text(watcher.name) },
        confirmButton = { TextButton(onClick = {
            update { it.copy(watchers = it.watchers.filterNot { item -> item.id == watcher.id }) }
            deletingWatcher = null
        }) { Text(stringResource(R.string.pulse_delete_watcher)) } },
        dismissButton = { TextButton(onClick = { deletingWatcher = null }) { Text(stringResource(R.string.chat_page_cancel)) } },
    ) }
    (editingWatcher ?: if (addWatcher) PulseWatcher() else null)?.let { draft -> PulseWatcherEditor(
        workspaceReady = pulseWorkspace?.shellStatus == "READY",
        initial = draft.takeIf { item -> config.watchers.any { it.id == item.id } },
        onDismiss = { addWatcher = false; editingWatcher = null },
        onSave = { watcher ->
            update { pulse ->
                val exists = pulse.watchers.any { it.id == watcher.id }
                val savedWatcher = if (watcher.target == WatcherTarget.COMMAND && pulseWorkspace?.shellStatus != "READY") {
                    watcher.copy(enabled = false)
                } else watcher
                pulse.copy(watchers = if (exists) pulse.watchers.map { if (it.id == watcher.id) savedWatcher else it }
                    else if (pulse.watchers.size < 20) pulse.watchers + savedWatcher else pulse.watchers)
            }
            addWatcher = false
            editingWatcher = null
        },
    ) }
    expandedActivity?.let { entry -> AlertDialog(
        onDismissRequest = { expandedActivity = null },
        title = { Text("${pulseTime(entry.at)} · ${pulseActivityType(entry.kind)}") },
        text = { Text(entry.summary.ifBlank { pulseResultLabel(entry.result) }) },
        confirmButton = {
            if (entry.reportPath.isNotBlank() && pulseWorkspace != null) TextButton(onClick = {
                nav.navigate(Screen.WorkspaceFileEditor(pulseWorkspace.id, me.rerere.workspace.WorkspaceStorageArea.FILES.name, entry.reportPath))
                expandedActivity = null
            }) { Text(stringResource(R.string.pulse_open_report)) }
        },
        dismissButton = { TextButton(onClick = { expandedActivity = null }) { Text(stringResource(R.string.pulse_close)) } },
    ) }
    deleting?.let { task -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.pulse_delete_task)) }, text = { Text(task.title) },
        confirmButton = { TextButton(onClick = {
            update { pulse -> pulse.copy(tasks = pulse.tasks.filterNot { it.id == task.id }) }; deleting = null
        }) { Text(stringResource(R.string.pulse_delete_task)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.chat_page_cancel)) } }) }
}

@Composable
private fun PulseWatcherEditor(workspaceReady: Boolean, initial: PulseWatcher?, onDismiss: () -> Unit, onSave: (PulseWatcher) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var source by remember(initial?.id) { mutableStateOf(initial?.source.orEmpty()) }
    var prompt by remember(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var minutesText by remember(initial?.id) { mutableStateOf((initial?.everyMinutes ?: 15).toString()) }
    var thresholdText by remember(initial?.id) { mutableStateOf((initial?.threshold ?: 5.0).toString().removeSuffix(".0")) }
    var target by remember(initial?.id) { mutableStateOf(initial?.target ?: WatcherTarget.URL) }
    val minutes = minutesText.toIntOrNull()?.coerceIn(10, 240) ?: 15
    val threshold = thresholdText.toDoubleOrNull()?.coerceIn(1.0, 50.0) ?: 5.0
    val validSource = when (target) {
        WatcherTarget.BATTERY -> true
        WatcherTarget.URL -> source.trim().startsWith("https://") || source.trim().startsWith("http://")
        WatcherTarget.COMMAND -> source.isNotBlank()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.pulse_add_watcher else R.string.pulse_edit_watcher)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pulse_watcher_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatcherTarget.entries.forEach { option ->
                        FilterChip(selected = target == option, onClick = { target = option }, label = { Text(pulseWatcherType(option)) })
                    }
                }
                if (target != WatcherTarget.BATTERY) OutlinedTextField(source, { source = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (target == WatcherTarget.URL) R.string.pulse_watcher_url else R.string.pulse_watcher_command)) })
                OutlinedTextField(minutesText, { value -> if (value.length <= 3 && value.all(Char::isDigit)) minutesText = value },
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pulse_watcher_interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (target == WatcherTarget.BATTERY) OutlinedTextField(thresholdText, { value ->
                    if (value.length <= 5 && value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) thresholdText = value
                },
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pulse_watcher_battery_threshold)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pulse_watcher_prompt)) }, minLines = 2)
                if (target == WatcherTarget.COMMAND && !workspaceReady) Text(stringResource(R.string.pulse_watcher_command_needs_workspace), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave((initial ?: PulseWatcher()).copy(name = name.trim().ifBlank { "Watcher" }, everyMinutes = minutes, target = target,
                source = source.trim(), threshold = threshold, prompt = prompt.trim()))
        }, enabled = validSource) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_page_cancel)) } },
    )
}

@Composable
private fun PulsePrompt(value: String, label: Int, onChange: (String) -> Unit, restore: () -> Unit) {
    Column {
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), minLines = 4,
            label = { Text(stringResource(label)) }, supportingText = { Text("${value.length}") })
        TextButton(onClick = restore) { Text(stringResource(R.string.pulse_restore_default)) }
    }
}

@Composable
private fun HeartbeatIntervalInput(value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hasFocus by remember { mutableStateOf(false) }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in 15..240

    fun commit() {
        val minutes = parsed?.coerceIn(15, 240) ?: value
        text = minutes.toString()
        onCommit(minutes)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input -> if (input.length <= 3 && input.all(Char::isDigit)) text = input },
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            if (hasFocus && !state.isFocused) commit()
            hasFocus = state.isFocused
        },
        singleLine = true,
        isError = text.isNotEmpty() && !isValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        label = { Text(stringResource(R.string.pulse_interval_minutes)) },
        supportingText = {
            Text(stringResource(R.string.pulse_interval_range, 15, 240))
        },
    )
}

@Composable
private fun pulseResultLabel(result: String): String = when {
    result == "OK" -> stringResource(R.string.pulse_result_ok)
    result == "REPORTED" -> stringResource(R.string.pulse_result_reported)
    result == "CANCELLED" -> stringResource(R.string.pulse_result_cancelled)
    result == "EXPIRED" -> stringResource(R.string.pulse_result_expired)
    result.startsWith("FAILED") -> stringResource(R.string.pulse_result_failed) + result.removePrefix("FAILED")
    else -> result
}

@Composable
private fun PulseLog(time: Long, result: String, detail: String) {
    ListItem(headlineContent = { Text("${pulseTime(time)} · ${pulseResultLabel(result)}") },
        supportingContent = { if (detail.isNotBlank()) Text(detail) })
}

@Composable
private fun PulsePriceInput(value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) {
        mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
    }
    var focused by remember { mutableStateOf(false) }
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed.isFinite() && parsed in 0.0..1_000_000.0
    fun commit() {
        val next = parsed?.takeIf { it.isFinite() }?.coerceIn(0.0, 1_000_000.0) ?: value
        text = next.toString()
        onCommit(next)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= 12 && input.count { it == '.' } <= 1 && input.all { it.isDigit() || it == '.' }) text = input
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            if (focused && !state.isFocused) commit()
            focused = state.isFocused
        },
        singleLine = true,
        isError = text.isNotEmpty() && !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        label = { Text(stringResource(R.string.pulse_price_per_million)) },
    )
}

@Composable
internal fun pulseUsageLine(tokens: Long, cost: Double?): String {
    val tokenText = stringResource(R.string.pulse_usage_tokens, compactPulseNumber(tokens))
    return if (cost == null) tokenText else stringResource(
        R.string.pulse_usage_estimate,
        tokenText,
        "%.4f".format(java.util.Locale.getDefault(), cost),
    )
}

@Composable
private fun pulseWatcherType(target: WatcherTarget): String = stringResource(when (target) {
    WatcherTarget.BATTERY -> R.string.pulse_watcher_type_battery
    WatcherTarget.URL -> R.string.pulse_watcher_type_url
    WatcherTarget.COMMAND -> R.string.pulse_watcher_type_command
})

@Composable
private fun pulseActivityType(kind: String): String = stringResource(when (kind) {
    "watcher" -> R.string.pulse_activity_watcher
    "task" -> R.string.pulse_activity_task
    else -> R.string.pulse_activity_heartbeat
})
