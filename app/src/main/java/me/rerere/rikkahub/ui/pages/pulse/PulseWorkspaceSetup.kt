package me.rerere.rikkahub.ui.pages.pulse

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.PulseWorkspaceProvisioner
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.pages.extensions.workspace.RootfsLocalInstallDialog
import me.rerere.rikkahub.ui.pages.extensions.workspace.RootfsPickerSheet
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.workspace.rootfs.RootfsCatalog
import me.rerere.workspace.rootfs.RootfsEntry
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

internal class PulseWorkspaceSetupContent(
    val header: @Composable () -> Unit,
    val overlays: @Composable () -> Unit,
)

@Composable
internal fun rememberPulseWorkspaceSetup(config: PulseConfig): PulseWorkspaceSetupContent {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val repository = koinInject<WorkspaceRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val workspaces by repository.listFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val currentWorkspace = remember(workspaces, config.workspaceId) {
        workspaces.firstOrNull {
            it.id == config.workspaceId?.toString() && it.owner == "pulse"
        }
    }
    val needsSetup = config.workspaceId == null || currentWorkspace == null

    var dismissed by remember(config.workspaceId) { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showLocalConfirm by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<RootfsInstallProgress?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    suspend fun finishInstall(workspaceId: String) {
        provisionPulseTemplates(repository, workspaceId)
        val id = Uuid.parse(workspaceId)
        settingsStore.update { current -> current.copy(pulse = current.pulse.copy(workspaceId = id, workspaceInitialized = true)) }
        Toast.makeText(context, R.string.pulse_workspace_ready, Toast.LENGTH_SHORT).show()
    }

    fun startInstall(install: suspend (String) -> Unit) {
        if (installing) return
        installing = true
        installError = null
        scope.launch {
            var createdId: String? = null
            try {
                val baseName = context.getString(R.string.pulse_workspace_name)
                var name = baseName
                var suffix = 2
                while (repository.isNameTaken(name, null)) name = "$baseName ${suffix++}"
                val workspace = repository.create(name, owner = "pulse")
                createdId = workspace.id
                install(workspace.id)
                finishInstall(workspace.id)
            } catch (cancelled: CancellationException) {
                createdId?.let { runCatching { repository.delete(it) } }
                throw cancelled
            } catch (failure: Exception) {
                createdId?.let { runCatching { repository.delete(it) } }
                installError = failure.message ?: context.getString(R.string.pulse_workspace_install_failed)
            } finally {
                progress = null
                installing = false
            }
        }
    }

    val localFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startInstall { workspaceId ->
            val (name, size) = resolveDisplayNameAndSize(context.contentResolver, uri)
            repository.installLocalRootfs(workspaceId, name, size, {
                context.contentResolver.openInputStream(uri) ?: error("Unable to read archive")
            }) { progress = it }
        }
    }

    val header: @Composable () -> Unit = {
        if (needsSetup) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dismissed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.pulse_workspace_not_configured),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { nav.navigate(Screen.PulseSettings(initialTab = 3)) }) {
                                Text(stringResource(R.string.pulse_workspace_go_settings))
                            }
                        }
                    } else {
                        Text(stringResource(R.string.pulse_workspace_setup_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.pulse_workspace_setup_message))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showPicker = true }) { Text(stringResource(R.string.pulse_workspace_install)) }
                            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.pulse_workspace_later)) }
                        }
                        installError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    val overlays: @Composable () -> Unit = {
        if (showPicker) RootfsPickerSheet(
            freeBytes = context.filesDir.usableSpace,
            onDismiss = { showPicker = false },
            onPick = { entry: RootfsEntry -> startInstall { id -> repository.installRootfs(id, entry) { progress = it } } },
            onCustom = { url, sha -> startInstall { id -> repository.installRootfsUrl(id, url, sha) { progress = it } } },
            onLocal = { showLocalConfirm = true },
        )
        if (showLocalConfirm) RootfsLocalInstallDialog(
            onDismiss = { showLocalConfirm = false },
            onConfirm = {
                showLocalConfirm = false
                localFilePicker.launch(arrayOf("application/gzip", "application/x-tar", "*/*"))
            },
        )
        if (installing) AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.pulse_workspace_installing)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress?.let { stringResource(R.string.pulse_workspace_install_progress, it.bytesRead / (1024 * 1024)) }
                        ?: stringResource(R.string.pulse_workspace_install_preparing))
                }
            },
            confirmButton = {},
        )
    }

    return PulseWorkspaceSetupContent(header, overlays)
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

private fun resolveDisplayNameAndSize(resolver: ContentResolver, uri: Uri): Pair<String, Long?> =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use "rootfs.tar.gz" to null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0 } else null
        (name ?: "rootfs.tar.gz") to size
    } ?: ("rootfs.tar.gz" to null)
