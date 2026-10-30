package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FolderCode
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkspacePage(scope: String? = null, vm: WorkspaceVM = koinViewModel()) {
    val navController = LocalNavController.current
    val allWorkspaces by vm.workspaces.collectAsStateWithLifecycle()
    val workspaces = remember(allWorkspaces, scope) {
        if (scope == null) allWorkspaces else allWorkspaces.filter { it.owner == null || it.owner == scope }
    }
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var ownerTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.workspace_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.dismissSaveError(); showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workspaces.isEmpty()) {
                item {
                    ExtensionEmptyState(
                        message = stringResource(R.string.workspace_page_empty),
                        description = stringResource(R.string.workspace_page_empty_desc),
                        icon = HugeIcons.FolderCode,
                    )
                }
            }

            items(workspaces, key = { it.id }) { workspace ->
                WorkspaceItem(
                    workspace = workspace,
                    onRename = { editTarget = workspace },
                    onSetOwner = { vm.dismissSaveError(); ownerTarget = workspace },
                    onDelete = { deleteTarget = workspace },
                    onOpen = { navController.navigate(Screen.WorkspaceDetail(workspace.id)) },
                )
            }
        }
    }

    if (showAddDialog) {
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_create),
            initialName = "",
            existingNames = allWorkspaces.map { it.name.trim() }.toSet(),
            showOwner = true,
            initialOwner = scope,
            saving = saving,
            error = saveError,
            onDismiss = { showAddDialog = false; vm.dismissSaveError() },
            onConfirm = { name, owner ->
                vm.create(name, owner) { showAddDialog = false }
            },
        )
    }

    editTarget?.let { workspace ->
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_rename),
            initialName = workspace.name,
            existingNames = allWorkspaces.filter { it.id != workspace.id }.map { it.name.trim() }.toSet(),
            onDismiss = { editTarget = null },
            onConfirm = { name, _ ->
                vm.rename(workspace, name)
                editTarget = null
            },
        )
    }

    ownerTarget?.let { workspace ->
        WorkspaceOwnerDialog(
            workspace = workspace,
            bound = settings.assistants.any { it.workspaceId?.toString() == workspace.id } ||
                settings.groups.any { it.workspaceId?.toString() == workspace.id },
            saving = saving,
            error = saveError,
            onDismiss = { ownerTarget = null; vm.dismissSaveError() },
            onConfirm = { owner -> vm.setOwner(workspace, owner) { ownerTarget = null } },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.workspace_page_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.workspace_page_delete_confirm))
    }
}

@Composable
private fun WorkspaceItem(
    workspace: WorkspaceEntity,
    onRename: () -> Unit,
    onSetOwner: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(HugeIcons.FolderCode)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = workspace.name,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = workspace.shellStatus.toShellStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(when (workspace.owner) {
                        "chat" -> R.string.workspace_owner_chat
                        "group" -> R.string.workspace_owner_group
                        else -> R.string.workspace_owner_unassigned
                    }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ItemActionMenu(
                    actions = buildList {
                        add(ItemAction(
                            text = stringResource(R.string.common_rename),
                            icon = HugeIcons.PencilEdit01,
                            onClick = onRename,
                        ))
                        add(ItemAction(
                            text = stringResource(R.string.workspace_set_owner),
                            icon = HugeIcons.Settings03,
                            onClick = onSetOwner,
                        ))
                        add(ItemAction(
                            text = stringResource(R.string.common_delete),
                            icon = HugeIcons.Delete01,
                            destructive = true,
                            onClick = onDelete,
                        ))
                    }
                )
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.common_rename),
                        icon = HugeIcons.PencilEdit01,
                        onClick = onRename,
                    ),
                    ItemAction(
                        text = stringResource(R.string.common_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@Composable
private fun EditWorkspaceDialog(
    title: String,
    initialName: String,
    existingNames: Set<String>,
    showOwner: Boolean = false,
    initialOwner: String? = null,
    saving: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var owner by rememberSaveable(initialOwner) { mutableStateOf(initialOwner) }
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotEmpty() && trimmedName in existingNames

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.workspace_page_name)) },
                    singleLine = true,
                    enabled = !saving,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text(stringResource(R.string.workspace_page_name_duplicate)) }
                    } else null,
                )
                if (showOwner) {
                    WorkspaceOwnerSelect(owner) {
                        if (!saving && owner != it) {
                            owner = it
                        }
                    }
                }
                error?.let { Text(it.ifBlank { stringResource(R.string.workspace_detail_settings_save_failed) },
                    color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName, owner) },
                enabled = !saving && name.isNotBlank() && !isDuplicate,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun WorkspaceOwnerSelect(owner: String?, onSelect: (String?) -> Unit) {
    Text(stringResource(R.string.workspace_page_owner))
    Select(
        options = listOf(null, "chat", "group"),
        selectedOption = owner,
        onOptionSelected = onSelect,
        modifier = Modifier.fillMaxWidth(),
        optionToString = { value -> stringResource(when (value) {
            "chat" -> R.string.workspace_owner_chat
            "group" -> R.string.workspace_owner_group
            else -> R.string.workspace_owner_unassigned
        }) },
    )
}

@Composable
private fun WorkspaceOwnerDialog(
    workspace: WorkspaceEntity,
    bound: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var owner by rememberSaveable(workspace.id) { mutableStateOf(workspace.owner) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.workspace_set_owner)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceOwnerSelect(owner) { if (!saving) owner = it }
                if (bound && owner != workspace.owner) Text(stringResource(R.string.workspace_owner_binding_notice),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it.ifBlank { stringResource(R.string.workspace_detail_settings_save_failed) },
                    color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(owner) }, enabled = !saving) {
            Text(stringResource(R.string.common_save))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) {
            Text(stringResource(R.string.common_cancel))
        } },
    )
}
