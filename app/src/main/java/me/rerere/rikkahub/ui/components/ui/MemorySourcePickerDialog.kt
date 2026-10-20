package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryGroup
import me.rerere.rikkahub.data.repository.MemoryMigrationMode
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject

data class MemorySource(val scope: String, val label: String, val category: String, val count: Int)

internal fun chatMemorySources(
    assistants: List<Assistant>, groups: List<MemoryGroup>, counts: Map<String, Int>,
    globalLabel: String, groupLabel: String, assistantLabel: String,
): List<MemorySource> {
    val scopes = (assistants.map(MemoryRepository::scopeOf) + MemoryRepository.GLOBAL_MEMORY_ID +
        groups.map { MemoryRepository.scopeOf(it.id) }).distinct()
    return scopes.mapNotNull { scope ->
        val count = counts[scope] ?: 0
        if (count == 0) return@mapNotNull null
        val (label, category) = when {
            scope == MemoryRepository.GLOBAL_MEMORY_ID -> globalLabel to globalLabel
            scope.startsWith(MemoryRepository.GROUP_MEMORY_PREFIX) ->
                (groups.find { MemoryRepository.scopeOf(it.id) == scope }?.name ?: return@mapNotNull null) to groupLabel
            else -> (assistants.find { it.id.toString() == scope }?.name ?: return@mapNotNull null) to assistantLabel
        }
        MemorySource(scope, label, category, count)
    }
}

@Composable
fun MemorySourcePickerDialog(
    show: Boolean, title: String, sources: List<MemorySource>,
    onPick: (MemorySource) -> Unit, onDismiss: () -> Unit, loading: Boolean = false,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                else if (sources.isEmpty()) Text(stringResource(R.string.memory_import_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else sources.groupBy { it.category }.forEach { (category, entries) ->
                    Text(category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    entries.forEach { source ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(source) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(source.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(source.count.toString(), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** UI-only orchestration of the existing F010 picker and migration engine. */
@Composable
internal fun MemoryImportDialogs(
    targetScope: String, targetLabel: String, showSources: Boolean, onDismissSources: () -> Unit,
    loadSources: suspend () -> List<MemorySource>, sharedCount: (String) -> Int = { 0 },
) = key(targetScope) {
    val repository: MemoryRepository = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val failure = stringResource(R.string.error_title_operation)
    var sources by remember { mutableStateOf(emptyList<MemorySource>()) }
    var loading by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<MemorySource?>(null) }
    var candidates by remember { mutableStateOf(emptyList<AssistantMemory>()) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var mode by remember { mutableStateOf(MemoryMigrationMode.COPY) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickJob by remember { mutableStateOf<Job?>(null) }
    val currentLoader by rememberUpdatedState(loadSources)
    LaunchedEffect(showSources) {
        if (!showSources) return@LaunchedEffect
        loading = true
        sources = emptyList()
        try { sources = currentLoader().filter { it.scope != targetScope && it.count > 0 }.distinctBy { it.scope } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { toaster.show(failure, type = ToastType.Error); onDismissSources() }
        finally { loading = false }
    }
    MemorySourcePickerDialog(showSources, stringResource(R.string.memory_import_title), sources,
        loading = loading || picking || saving,
        onDismiss = { pickJob?.cancel(); picking = false; onDismissSources() },
        onPick = { picked ->
            if (!picking && !saving) {
                picking = true
                pickJob = coroutineScope.launch {
                    try {
                        candidates = repository.getMemoriesOfAssistant(picked.scope)
                        selected = emptySet()
                        mode = MemoryMigrationMode.COPY
                        source = picked
                        onDismissSources()
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { toaster.show(failure, type = ToastType.Error) }
                    finally { picking = false }
                }
            }
        })
    val usedBy = source?.let { sharedCount(it.scope) } ?: 0
    MemoryPickerDialog(
        show = source != null,
        title = stringResource(R.string.memory_picker_migrate_title),
        subtitle = stringResource(R.string.memory_picker_migrate_subtitle, targetLabel),
        memories = candidates, selectedIds = selected, onSelectedIdsChange = { selected = it },
        modeSwitch = MemoryPickerModeSwitch(mode) { mode = it },
        warning = if (mode == MemoryMigrationMode.MOVE && usedBy > 1 && source?.scope?.let {
                it == MemoryRepository.GLOBAL_MEMORY_ID || it.startsWith(MemoryRepository.GROUP_MEMORY_PREFIX)
            } == true) stringResource(R.string.memory_picker_warn_move_shared, usedBy) else null,
        confirmText = stringResource(R.string.confirm),
        onDismiss = { source = null },
        onConfirm = {
            val picked = source
            if (picked != null && !saving) {
                val ids = selected.intersect(candidates.map { it.id }.toSet())
                val chosenMode = mode
                saving = true
                source = null
                coroutineScope.launch {
                    try {
                        repository.migrateMemories(picked.scope, targetScope, ids, chosenMode)
                        toaster.show(context.getString(R.string.memory_import_done, ids.size), type = ToastType.Success)
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { toaster.show(failure, type = ToastType.Error) }
                    finally { saving = false }
                }
            }
        },
    )
}
