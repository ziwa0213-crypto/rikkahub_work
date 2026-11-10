package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.PulseWorkspaceProvisioner
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.extensions.workspace.toShellStatusLabel
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

internal class PulseWorkspaceSetupContent(
    val header: @Composable () -> Unit,
    val overlays: @Composable () -> Unit,
)

@Composable
internal fun rememberPulseWorkspaceSetup(config: PulseConfig): PulseWorkspaceSetupContent {
    val nav = LocalNavController.current
    val repository = koinInject<WorkspaceRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val workspaces by repository.listFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWorkspace = workspaces.firstOrNull { it.id == config.workspaceId?.toString() && it.owner == "pulse" }
    var setupError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    // A shared mutex in the provisioner also covers simultaneous chat/settings compositions.
    LaunchedEffect(config.workspaceId, workspaces.map { it.id to it.name }, retry) {
        try {
            val loaded = settingsStore.awaitLoaded().pulse
            val workspace = PulseWorkspaceProvisioner.ensureWorkspace(repository, loaded.workspaceId)
            provisionPulseTemplates(repository, workspace.id)
            settingsStore.update { current ->
                current.copy(pulse = current.pulse.copy(workspaceId = Uuid.parse(workspace.id), workspaceInitialized = true))
            }
            setupError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            setupError = failure.message ?: "Pulse"
        }
    }

    val header: @Composable () -> Unit = {
        currentWorkspace?.let { workspace ->
            Card(onClick = { nav.navigate(Screen.WorkspaceDetail(workspace.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workspace.name, style = MaterialTheme.typography.titleSmall)
                    Text(workspace.shellStatus.toShellStatusLabel(), style = MaterialTheme.typography.bodyMedium)
                    if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
                        Text(stringResource(R.string.pulse_workspace_setup_message), style = MaterialTheme.typography.bodySmall)
                        // The existing detail page owns the installer, picker and rollback flow.
                        TextButton(onClick = { nav.navigate(Screen.WorkspaceDetail(workspace.id)) }) {
                            Text(stringResource(R.string.pulse_workspace_install))
                        }
                    }
                }
            }
        }
        setupError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_page_notice_retry)) }
        }
    }
    return PulseWorkspaceSetupContent(header, {})
}

internal suspend fun provisionPulseTemplates(repository: WorkspaceRepository, workspaceId: String): Int {
    val existing = repository.listFiles(workspaceId, WorkspaceStorageArea.FILES, "").mapTo(hashSetOf()) { it.name }
    return PulseWorkspaceProvisioner.provision { path, content ->
        if (path in existing) false else {
            try {
                repository.writeText(workspaceId, path, content, overwrite = false)
                existing += path
                true
            } catch (failure: IllegalArgumentException) {
                if (failure.message?.contains("already exists", ignoreCase = true) == true) false else throw failure
            }
        }
    }.size
}
