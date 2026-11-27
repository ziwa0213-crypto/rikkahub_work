package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun CharacterTransferPage() {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val store: SettingsStore = koinInject()
    val repo = CharacterTransferVM(store, koinInject(), koinInject(), koinInject(), koinInject())
    var pending by remember { mutableStateOf(setOf<Uuid>()) }
    var current by remember { mutableStateOf<Uuid?>(null) }
    var target by remember { mutableStateOf(Uuid.random()) }
    var step by remember { mutableIntStateOf(0) }
    var move by remember { mutableStateOf(false) }
    var moveMemory by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var memory by remember { mutableStateOf<TransferMemory?>(null) }
    var paths by remember { mutableStateOf(setOf<String>()) }
    var rows by remember { mutableStateOf(setOf<Int>()) }
    val assistant = settings.assistants.firstOrNull { it.id == current }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
            finally { busy = false }
        }
    }
    fun commit(selection: TransferMemory?) {
        val id = current ?: return
        run {
            val missing = repo.transfer(id, move, selection, selection != null && moveMemory, target)
            pending = pending - id
            current = null
            memory = null
            step = 0
            if (missing > 0) toaster.show(context.getString(R.string.character_transfer_missing_attachments, missing))
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.character_transfer)) }, navigationIcon = { if (!busy) BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (step != 3) {
                LazyColumn(Modifier.weight(1f)) {
                    items(settings.assistants, key = { it.id.toString() }) { item ->
                        Row {
                            Checkbox(item.id in pending, onCheckedChange = { checked ->
                                pending = if (checked) pending + item.id else pending - item.id
                            }, enabled = step == 0 && !busy)
                            Text(item.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                Row {
                    TextButton(enabled = pending.isNotEmpty() && step == 0 && !busy, onClick = {
                        current = pending.first()
                        target = Uuid.random()
                        moveMemory = false
                        step = 1
                    }) { Text(stringResource(R.string.character_transfer_next)) }
                    TextButton(enabled = pending.isEmpty() && step == 0 && !busy, onClick = {
                        run { repo.finish(); nav.clearAndNavigate(Screen.CharacterList) }
                    }) { Text(stringResource(R.string.character_transfer_finish)) }
                }
            } else {
                val candidates = memory
                Text(stringResource(R.string.character_memory_merge_info))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy && candidates != null, onClick = {
                        candidates?.selectAll()?.let { selection ->
                            paths = selection.filePaths
                            rows = selection.rowIds
                        }
                    }) { Text(stringResource(R.string.memory_picker_select_all)) }
                    TextButton(enabled = !busy && candidates != null, onClick = {
                        candidates?.invertSelection(TransferMemorySelection(paths, rows))?.let { selection ->
                            paths = selection.filePaths
                            rows = selection.rowIds
                        }
                    }) { Text(stringResource(R.string.memory_picker_invert)) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(candidates?.files.orEmpty(), key = { it.path }) { file ->
                        Row { Checkbox(file.path in paths, { if (it) paths += file.path else paths -= file.path }, enabled = !busy); Text(file.path, Modifier.padding(top = 12.dp)) }
                    }
                    items(candidates?.rows.orEmpty(), key = { it.id }) { row ->
                        Row { Checkbox(row.id in rows, { if (it) rows += row.id else rows -= row.id }, enabled = !busy); Text(row.content, Modifier.padding(top = 12.dp)) }
                    }
                }
                val moveBlock = if (candidates != null && assistant != null) settings.memoryMoveBlock(assistant.id, candidates.scope) else null
                val allowMove = moveBlock == MemoryMoveBlock.NONE
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    Row(
                        Modifier.weight(1f).selectable(
                            selected = !moveMemory, enabled = !busy, role = Role.RadioButton,
                            onClick = { moveMemory = false },
                        ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !moveMemory, onClick = null, enabled = !busy)
                        Text(stringResource(R.string.character_copy))
                    }
                    Row(
                        Modifier.weight(1f).selectable(
                            selected = moveMemory, enabled = allowMove && !busy, role = Role.RadioButton,
                            onClick = { moveMemory = true },
                        ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = moveMemory, onClick = null, enabled = allowMove && !busy)
                        Text(stringResource(R.string.character_move))
                    }
                }
                val moveBlockText = when (moveBlock) {
                    MemoryMoveBlock.GLOBAL -> R.string.character_memory_move_blocked_global
                    MemoryMoveBlock.SHARED_WITH_ASSISTANT -> R.string.character_memory_move_blocked_shared
                    MemoryMoveBlock.REFERENCED_BY_CHARACTER -> R.string.character_memory_move_blocked_referenced
                    else -> null
                }
                if (moveBlockText != null) {
                    Text(stringResource(moveBlockText), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(enabled = !busy, onClick = {
                    commit(candidates?.copy(files = candidates.files.filter { it.path in paths }, rows = candidates.rows.filter { it.id in rows }))
                }) { Text(stringResource(R.string.character_transfer_next)) }
            }
        }
    }
    if (step == 1 && assistant != null) AlertDialog(onDismissRequest = { step = 0 },
        title = { Text(stringResource(R.string.character_transfer_assistant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(assistant.name)
                val moveBlockText = when (settings.assistantMoveBlock(assistant.id)) {
                    AssistantMoveBlock.DEFAULT_ASSISTANT -> R.string.character_move_blocked_default_assistant
                    AssistantMoveBlock.GROUP_MEMBER -> R.string.character_move_blocked_group_member
                    AssistantMoveBlock.NONE -> null
                }
                if (moveBlockText != null) {
                    Text(stringResource(moveBlockText), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { move = false; step = 2 }) { Text(stringResource(R.string.character_copy)) } },
        dismissButton = { TextButton(enabled = settings.canMoveToCharacter(assistant.id), onClick = { move = true; step = 2 }) {
            Text(stringResource(R.string.character_move))
        } },
    )
    if (step == 2 && assistant != null) AlertDialog(onDismissRequest = { if (!busy) step = 1 },
        title = { Text(stringResource(R.string.character_transfer_memory)) },
        text = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            run { memory = repo.candidates(assistant.id); paths = emptySet(); rows = emptySet(); step = 3 }
        }) { Text(stringResource(R.string.character_transfer_next)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { commit(null) }) { Text(stringResource(R.string.character_migration_decline)) } },
    )
}
