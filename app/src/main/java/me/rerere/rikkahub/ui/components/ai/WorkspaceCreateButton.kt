package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.WorkspaceOwnerScope
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import org.koin.compose.koinInject

@Composable
internal fun WorkspaceCreateButton(owner: WorkspaceOwnerScope, workspaces: List<WorkspaceEntity>, onCreated: (String) -> Unit) {
    val repository: WorkspaceRepository = koinInject()
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val duplicate = workspaces.any { it.name.trim() == name.trim() }
    TextButton(onClick = {
        name = ""; error = null
        show = true
    }) { Text(stringResource(R.string.workspace_page_create)) }
    if (show) {
        AlertDialog(
            onDismissRequest = { if (!busy) show = false },
            title = { Text(stringResource(R.string.workspace_page_create)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text(stringResource(R.string.workspace_page_name)) },
                        enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = duplicate,
                        supportingText = if (duplicate) { { Text(stringResource(R.string.workspace_page_name_duplicate)) } } else null)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && name.isNotBlank() && !duplicate, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val created = repository.create(name, owner.value)
                            onCreated(created.id)
                            show = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { show = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
