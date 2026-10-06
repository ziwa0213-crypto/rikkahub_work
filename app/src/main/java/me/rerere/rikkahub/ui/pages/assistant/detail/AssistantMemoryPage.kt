package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryGroup
import me.rerere.rikkahub.data.repository.MemoryMigrationMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.MemoryGroupSelector
import me.rerere.rikkahub.ui.components.ui.MemoryPickerDialog
import me.rerere.rikkahub.ui.components.ui.MemoryPickerModeSwitch
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val memoryGroupDeleteImpact by vm.memoryGroupDeleteImpact.collectAsStateWithLifecycle()
    val pendingMemoryMigration by vm.pendingMemoryMigration.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantMemoryContent(
            innerPadding = innerPadding,
            assistant = assistant,
            memories = memories,
            memoryGroups = settings.memoryGroups,
            memoryGroupDeleteImpact = memoryGroupDeleteImpact,
            pendingMemoryMigration = pendingMemoryMigration,
            onUpdateAssistant = { vm.update(it) },
            onRequestScopeChange = vm::requestMemoryScopeChange,
            onConfirmMemoryScopeChange = vm::confirmMemoryScopeChange,
            onDismissMemoryScopeChange = vm::dismissMemoryScopeChange,
            onCreateMemoryGroup = vm::createMemoryGroup,
            onRenameMemoryGroup = vm::renameMemoryGroup,
            onRequestDeleteMemoryGroup = vm::requestMemoryGroupDeletion,
            onConfirmDeleteMemoryGroup = vm::confirmMemoryGroupDeletion,
            onDismissDeleteMemoryGroup = vm::dismissMemoryGroupDeletion,
            onDeleteMemory = { vm.deleteMemory(it) },
            onAddMemory = { vm.addMemory(it) },
            onUpdateMemory = { vm.updateMemory(it) }
        )
    }
}

@Composable
private fun AssistantMemoryContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    memories: List<AssistantMemory>,
    memoryGroups: List<MemoryGroup>,
    memoryGroupDeleteImpact: MemoryGroupDeleteImpact?,
    pendingMemoryMigration: PendingMemoryMigration?,
    onUpdateAssistant: (Assistant) -> Unit,
    onRequestScopeChange: (Boolean, Uuid?, String) -> Unit,
    onConfirmMemoryScopeChange: (Set<Int>, MemoryMigrationMode) -> Unit,
    onDismissMemoryScopeChange: () -> Unit,
    onCreateMemoryGroup: (String) -> Unit,
    onRenameMemoryGroup: (MemoryGroup, String) -> Unit,
    onRequestDeleteMemoryGroup: (MemoryGroup) -> Unit,
    onConfirmDeleteMemoryGroup: () -> Unit,
    onDismissDeleteMemoryGroup: () -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }
    var showMemoryGroupSelector by remember { mutableStateOf(false) }
    var pickerSelectedIds by remember(pendingMemoryMigration) { mutableStateOf(emptySet<Int>()) }
    var pickerMode by remember(pendingMemoryMigration) { mutableStateOf(MemoryMigrationMode.COPY) }
    val privateMemoryLabel = stringResource(R.string.memory_group_private)
    val globalMemoryLabel = stringResource(R.string.memory_group_global)

    val currentMemoryScopeName = when {
        assistant.useGlobalMemory -> stringResource(R.string.memory_group_global)
        assistant.memoryGroupId != null -> memoryGroups
            .find { it.id == assistant.memoryGroupId }
            ?.name
            ?: stringResource(R.string.memory_group_private)
        else -> stringResource(R.string.memory_group_private)
    }

    MemoryGroupSelector(
        show = showMemoryGroupSelector,
        assistant = assistant,
        memoryGroups = memoryGroups,
        onDismiss = { showMemoryGroupSelector = false },
        onSelectPrivate = {
            onRequestScopeChange(
                false,
                null,
                privateMemoryLabel,
            )
            showMemoryGroupSelector = false
        },
        onSelectGlobal = {
            onRequestScopeChange(
                true,
                null,
                globalMemoryLabel,
            )
            showMemoryGroupSelector = false
        },
        onSelectGroup = { group ->
            onRequestScopeChange(
                false,
                group.id,
                group.name,
            )
            showMemoryGroupSelector = false
        },
        onCreateGroup = onCreateMemoryGroup,
        onRenameGroup = onRenameMemoryGroup,
        onDeleteGroup = { group ->
            showMemoryGroupSelector = false
            onRequestDeleteMemoryGroup(group)
        },
    )

    MemoryPickerDialog(
        show = pendingMemoryMigration != null,
        title = stringResource(R.string.memory_picker_migrate_title),
        subtitle = pendingMemoryMigration?.let {
            stringResource(R.string.memory_picker_migrate_subtitle, it.targetLabel)
        },
        memories = pendingMemoryMigration?.candidates.orEmpty(),
        selectedIds = pickerSelectedIds,
        onSelectedIdsChange = { pickerSelectedIds = it },
        modeSwitch = MemoryPickerModeSwitch(
            value = pickerMode,
            onValueChange = { pickerMode = it },
        ),
        warning = pendingMemoryMigration?.takeIf {
            pickerMode == MemoryMigrationMode.MOVE &&
                it.fromScope != assistant.id.toString() &&
                it.selectorUsedByOthers > 1
        }?.let {
            stringResource(R.string.memory_picker_warn_move_shared, it.selectorUsedByOthers - 1)
        },
        confirmText = stringResource(R.string.confirm),
        onConfirm = {
            onConfirmMemoryScopeChange(pickerSelectedIds, pickerMode)
        },
        onDismiss = onDismissMemoryScopeChange,
    )

    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                TextField(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeReminderIntervalDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    // 记忆对话框
    memoryDialogState.EditStateContent { memory, update ->
        AlertDialog(
            onDismissRequest = {
                memoryDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.assistant_page_manage_memory_title))
            },
            text = {
                TextField(
                    value = memory.content,
                    onValueChange = {
                        update(memory.copy(content = it))
                    },
                    label = {
                        Text(stringResource(R.string.assistant_page_manage_memory_title))
                    },
                    minLines = 2,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_memory_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableMemory,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableMemory = it
                                )
                            )
                        }
                    )
                }
            )
            item(
                onClick = if (assistant.enableMemory) {
                    { showMemoryGroupSelector = true }
                } else {
                    null
                },
                modifier = Modifier.alpha(if (assistant.enableMemory) 1f else 0.38f),
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_group)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_memory_group_desc),
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentMemoryScopeName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                        Icon(HugeIcons.ArrowRight01, contentDescription = null)
                    }
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_recent_chats_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableRecentChatsReference,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableRecentChatsReference = it
                                )
                            )
                        }
                    )
                }
            )
        }

        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_time_reminder_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableTimeReminder,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableTimeReminder = it
                                )
                            )
                        }
                    )
                }
            )
            if (assistant.enableTimeReminder) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_desc)) },
                    trailingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_value, assistant.timeReminderIntervalMinutes)) },
                    onClick = {
                        timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showTimeReminderIntervalDialog = true
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterStart)
            )

            IconButton(
                onClick = {
                    memoryDialogState.open(AssistantMemory(0, ""))
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = null
                )
            }
        }

        memories.fastForEach { memory ->
            key(memory.id) {
                MemoryItem(
                    memory = memory,
                    onEditMemory = {
                        memoryDialogState.open(it)
                    },
                    onDeleteMemory = {
                        pendingDeleteMemory = it
                    }
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = memoryGroupDeleteImpact != null,
        title = stringResource(R.string.memory_group_delete_confirm_title),
        confirmText = stringResource(R.string.memory_group_delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirmDeleteMemoryGroup,
        onDismiss = onDismissDeleteMemoryGroup,
        text = {
            memoryGroupDeleteImpact?.let { impact ->
                Text(
                    stringResource(
                        R.string.memory_group_delete_confirm,
                        impact.group.name,
                        impact.memoryCount,
                        impact.memberCount,
                    )
                )
            }
        },
    )

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.content.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit
) {
    Card(
        onClick = { onEditMemory(memory) },
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = memory.content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { onDeleteMemory(memory) },
                    ),
                )
            )
        }
    }
}
