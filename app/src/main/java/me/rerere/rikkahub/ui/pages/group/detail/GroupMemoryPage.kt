package me.rerere.rikkahub.ui.pages.group.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.dao.MemoryEntityDAO
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.MemoryImportDialogs
import me.rerere.rikkahub.ui.components.ui.chatMemorySources
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.ItemizedMemoryTab
import me.rerere.rikkahub.ui.pages.group.GroupVM
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject

@Composable
fun GroupMemoryPage(groupId: String, conversationId: String, vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = settings.groups.find { it.id.toString() == groupId } ?: return
    val conversation = group.conversationIds.firstOrNull { it.toString() == conversationId } ?: return
    val repository: MemoryRepository = koinInject()
    val memoryScope = MemoryRepository.scopeOfChatGroup(group.id, conversation)
    val memories by remember(repository, memoryScope) { repository.getRowMemoriesFlow(memoryScope) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedIds by remember(memoryScope) { mutableStateOf(emptySet<Int>()) }
    var pendingDelete by remember(memoryScope) { mutableStateOf<Set<Int>?>(null) }
    var deleting by remember(memoryScope) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val operationFailed = stringResource(R.string.error_title_operation)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val memoryEntityDao: MemoryEntityDAO = koinInject()
    val globalLabel = stringResource(R.string.memory_group_global)
    val groupChatLabel = stringResource(R.string.memory_import_cat_group_chat)
    val assistantLabel = stringResource(R.string.memory_import_cat_assistant)
    val groupLabel = stringResource(R.string.assistant_page_memory_group)
    var showImport by remember(memoryScope) { mutableStateOf(false) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    LaunchedEffect(memories) {
        selectedIds = selectedIds.intersect(memories.mapTo(mutableSetOf()) { it.id })
    }

    fun run(action: suspend () -> Unit) = scope.launch {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            toaster.show(operationFailed, type = ToastType.Error)
        }
    }

    MemoryImportDialogs(
        targetScope = memoryScope,
        targetLabel = group.name,
        showSources = showImport,
        onDismissSources = { showImport = false },
        loadSources = {
            val counts = memoryEntityDao.getAllMemories().groupingBy { it.assistantId }.eachCount()
            chatMemorySources(
                settings.assistants,
                settings.memoryGroups,
                counts,
                globalLabel,
                groupLabel,
                assistantLabel,
            )
        },
        sharedCount = { scope -> settings.assistants.count { it.memoryId == scope } },
    )

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                val allIds = memories.mapTo(mutableSetOf()) { it.id }
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.assistant_page_memory_selected_count, selectedIds.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.containsAll(allIds)) emptySet() else allIds
                        }) {
                            Text(stringResource(
                                if (selectedIds.containsAll(allIds)) R.string.media_creation_page_deselect_all
                                else R.string.memory_picker_select_all
                            ))
                        }
                        IconButton(
                            enabled = !deleting,
                            onClick = { pendingDelete = selectedIds },
                        ) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = CustomColors.topBarColors,
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                    navigationIcon = { BackButton() },
                    actions = {
                        IconButton(onClick = { showImport = true }) {
                            Icon(
                                HugeIcons.Download01,
                                contentDescription = stringResource(R.string.memory_import_from_chat),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors,
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ItemizedMemoryTab(
                memoryScope = memoryScope,
                memories = memories,
                selectedIds = selectedIds,
                onSelectedIdsChange = { selectedIds = it },
                onAdd = { content -> run { repository.addRowMemory(memoryScope, content) } },
                onUpdate = { row -> run { repository.updateRowMemory(memoryScope, row.id, row.content) } },
                onRequestDelete = { ids -> pendingDelete = ids },
            )
        }
    }

    RikkaConfirmDialog(
        show = pendingDelete != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val ids = pendingDelete
            if (ids != null && !deleting) {
                deleting = true
                run {
                    try {
                        repository.deleteRowMemories(memoryScope, ids.toList())
                        selectedIds = selectedIds - ids
                        pendingDelete = null
                    } finally {
                        deleting = false
                    }
                }
            }
        },
        onDismiss = { if (!deleting) pendingDelete = null },
    ) {
        Text(stringResource(R.string.assistant_page_memory_delete_selected_confirm, pendingDelete?.size ?: 0))
    }
}
