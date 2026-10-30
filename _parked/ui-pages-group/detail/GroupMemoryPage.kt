package me.rerere.rikkahub.ui.pages.group.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.pages.group.GroupVM
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun GroupMemoryPage(groupId: String, conversationId: String, vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = settings.groups.find { it.id.toString() == groupId } ?: return
    val repository: MemoryRepository = koinInject()
    val id = runCatching { Uuid.parse(conversationId) }.getOrNull()?.takeIf { it in group.conversationIds } ?: return
    val memoryScope = MemoryRepository.scopeOfChatGroup(group.id, id)
    val memories by remember(repository, memoryScope) { repository.getMemoriesOfAssistantFlow(memoryScope) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val operationFailed = stringResource(R.string.error_title_operation)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember(memoryScope) { mutableStateOf<AssistantMemory?>(null) }
    var deleting by remember(memoryScope) { mutableStateOf<AssistantMemory?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showImport by remember(memoryScope) { mutableStateOf(false) }
    val memoryDao: MemoryDAO = koinInject()
    val globalLabel = stringResource(R.string.memory_group_global)
    val groupLabel = stringResource(R.string.assistant_page_memory_group)
    val assistantLabel = stringResource(R.string.memory_import_cat_assistant)
    MemoryImportDialogs(
        targetScope = memoryScope, targetLabel = group.name,
        showSources = showImport, onDismissSources = { showImport = false },
        loadSources = {
            // The repository intentionally has no getAllMemories facade; use the existing DAO once.
            val counts = memoryDao.getAllMemories().groupingBy { it.assistantId }.eachCount()
            chatMemorySources(settings.assistants, settings.memoryGroups, counts, globalLabel, groupLabel, assistantLabel)
        },
        sharedCount = { source -> settings.assistants.count { MemoryRepository.scopeOf(it) == source } },
    )
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(padding).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.assistant_page_manage_memory_title),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showImport = true }) {
                    Icon(HugeIcons.Download01, stringResource(R.string.memory_import_from_chat))
                }
                IconButton(onClick = { editing = AssistantMemory(0, "") }) {
                    Icon(HugeIcons.Add01, stringResource(R.string.add))
                }
            }
            memories.forEach { memory ->
                key(memory.id) {
                    Card(onClick = { editing = memory }, modifier = Modifier.fillMaxWidth(),
                        colors = CustomColors.cardColorsOnSurfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(memory.content, maxLines = 5, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            ItemActionMenu(listOf(ItemAction(stringResource(R.string.delete), HugeIcons.Delete01,
                                destructive = true, onClick = { deleting = memory })))
                        }
                    }
                }
            }
        }
    }
    editing?.let { memory ->
        AlertDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = { Text(stringResource(R.string.assistant_page_manage_memory_title)) },
            text = {
                TextField(memory.content, { editing = memory.copy(content = it) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
            }, confirmButton = {
                TextButton(enabled = !saving && memory.content.isNotBlank(), onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (memory.id == 0) repository.addMemory(memoryScope, memory.content)
                            else repository.updateContentInScope(memoryScope, memory.id, memory.content)
                            editing = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            toaster.show(operationFailed, type = ToastType.Error)
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.assistant_page_save)) }
            }, dismissButton = {
                TextButton(enabled = !saving, onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    RikkaConfirmDialog(deleting != null, stringResource(R.string.confirm_delete), stringResource(R.string.confirm),
        stringResource(R.string.cancel), onConfirm = {
            deleting?.let { memory -> scope.launch {
                try { repository.deleteMemoryInScope(memoryScope, memory.id) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { toaster.show(operationFailed, type = ToastType.Error) }
            } }
            deleting = null
        }, onDismiss = { deleting = null }) {
        Text(deleting?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis)
    }
}
