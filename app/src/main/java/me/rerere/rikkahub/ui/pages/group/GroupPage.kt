package me.rerere.rikkahub.ui.pages.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.group.components.*
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

@Composable
fun GroupPage(vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val duplicating by vm.isDuplicating.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Uuid?>(null) }
    val groups = settings.groups.filter { it.name.contains(query, ignoreCase = true) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Uuid ?: return@rememberReorderableLazyListState
        val toId = to.key as? Uuid ?: return@rememberReorderableLazyListState
        vm.reorderGroups(fromId, toId)
    }
    GroupListLayout(stringResource(R.string.group_settings_title), stringResource(R.string.group_search_groups),
        query, { query = it }, { creating = true }, listState = listState) {
        if (duplicating) item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.group_copying))
            }
        }
        if (groups.isEmpty()) item {
            GroupEmptyState(
                stringResource(if (query.isBlank()) R.string.group_empty_groups else R.string.group_no_matches),
                stringResource(if (query.isBlank()) R.string.group_empty_groups_hint else R.string.group_no_matches_hint),
            ) { if (query.isBlank()) TextButton(onClick = { creating = true }) { Text(stringResource(R.string.group_new)) } }
        }
        items(groups, key = { it.id }) { group ->
            ReorderableItem(reorderState, key = group.id) { dragging ->
                Card(onClick = { navController.navigate(Screen.GroupConfig(group.id.toString())) },
                    modifier = Modifier.fillMaxWidth().animateItem()
                        .then(longPressReorder(dragging, enabled = query.isBlank())),
                    colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GroupAvatar(group, settings, Modifier.size(48.dp), onClick = {
                            navController.navigate(Screen.GroupConfig(group.id.toString()))
                        })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.group_member_count, group.members.size), style = MaterialTheme.typography.labelSmall)
                            GroupTags(settings, group.tags)
                        }
                        ItemActionMenu(listOf(
                            ItemAction(stringResource(R.string.edit), HugeIcons.PencilEdit01,
                                onClick = { navController.navigate(Screen.GroupConfig(group.id.toString())) }),
                            ItemAction(stringResource(R.string.copy), HugeIcons.Copy01, onClick = {
                                vm.duplicateGroup(group) { count ->
                                    toaster.show(resources.getString(R.string.group_copied, count), type = ToastType.Success)
                                }
                            }),
                            ItemAction(stringResource(R.string.delete), HugeIcons.Delete01, destructive = true, onClick = { deleting = group.id }),
                        ))
                    }
                }
            }
        }
    }
    if (creating) NewGroupDialog(settings, { creating = false }, onCreate = { group ->
        vm.saveGroup(group) {
            creating = false
            toaster.show(resources.getString(R.string.group_created), type = ToastType.Success)
            navController.navigate(Screen.GroupConfig(group.id.toString()))
        }
    }, onCreateMember = { navController.navigate(Screen.GroupMembers) }, onUpdateTags = vm::updateGroupTags)
    RikkaConfirmDialog(deleting != null, stringResource(R.string.group_delete), stringResource(R.string.delete),
        stringResource(R.string.cancel), onConfirm = { deleting?.let { vm.deleteGroup(it) }; deleting = null },
        onDismiss = { deleting = null }) { Text(stringResource(R.string.group_delete_hint)) }
}
