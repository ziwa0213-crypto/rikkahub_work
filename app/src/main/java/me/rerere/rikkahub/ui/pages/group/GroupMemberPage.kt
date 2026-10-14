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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.group.components.*
import me.rerere.rikkahub.ui.theme.CustomColors
import com.dokar.sonner.ToastType
import kotlin.uuid.Uuid

@Composable
fun GroupMemberPage(vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<GroupMember?>(null) }
    var deleting by remember { mutableStateOf<GroupMember?>(null) }
    var showTagsNotice by remember { mutableStateOf(true) }
    val members = settings.groupMembers.filter { it.name.contains(query, ignoreCase = true) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Uuid ?: return@rememberReorderableLazyListState
        val toId = to.key as? Uuid ?: return@rememberReorderableLazyListState
        vm.reorderMembers(fromId, toId)
    }
    GroupListLayout(stringResource(R.string.group_members_title), stringResource(R.string.group_search_members),
        query, { query = it }, { editing = GroupMember() }, listState = listState) {
        if (showTagsNotice) item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.group_tags_migrated_hint), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { showTagsNotice = false }) { Icon(HugeIcons.Cancel01, stringResource(R.string.cancel)) }
            }
        }
        if (members.isEmpty()) item {
            GroupEmptyState(
                stringResource(if (query.isBlank()) R.string.group_empty_members else R.string.group_no_matches),
                stringResource(if (query.isBlank()) R.string.group_empty_members_hint else R.string.group_no_matches_hint),
            ) { if (query.isBlank()) TextButton(onClick = { editing = GroupMember() }) { Text(stringResource(R.string.group_new_member)) } }
        }
        items(members, key = { it.id }) { member ->
            ReorderableItem(reorderState, key = member.id) { dragging ->
                Card(onClick = { editing = member }, modifier = Modifier.fillMaxWidth().animateItem()
                    .then(longPressReorder(dragging, enabled = query.isBlank())),
                    colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UIAvatar(member.name, member.avatar, modifier = Modifier.size(48.dp), onClick = { editing = member })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(member.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Tag(type = TagType.SUCCESS, modifier = Modifier.widthIn(max = 240.dp)) {
                                Text(stringResource(R.string.group_model_label, groupModelName(settings, member.chatModelId)),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            GroupTags(settings, member.tags)
                        }
                        ItemActionMenu(listOf(
                            ItemAction(stringResource(R.string.edit), HugeIcons.PencilEdit01, onClick = { editing = member }),
                            ItemAction(stringResource(R.string.copy), HugeIcons.Copy01, onClick = {
                                val copy = member.copy(id = Uuid.random(), name = resources.getString(R.string.group_copy_name, member.name),
                                    createdAt = System.currentTimeMillis())
                                if (copy.tags.any { id -> settings.groupTags.any { it.id == id } }) vm.saveMember(copy)
                                else editing = copy
                            }),
                            ItemAction(stringResource(R.string.delete), HugeIcons.Delete01, destructive = true, onClick = { deleting = member }),
                        ))
                    }
                }
            }
        }
    }
    editing?.let { initial ->
        NewMemberDialog(settings, initial, { editing = null }, vm::updateGroupTags) { member ->
            vm.saveMember(member) {
                editing = null
                toaster.show(resources.getString(R.string.group_member_saved), type = ToastType.Success)
            }
        }
    }
    RikkaConfirmDialog(deleting != null, stringResource(R.string.group_delete_member), stringResource(R.string.delete),
        stringResource(R.string.cancel), onConfirm = { deleting?.let { vm.deleteMember(it.id) }; deleting = null },
        onDismiss = { deleting = null }) { Text(stringResource(R.string.group_delete_member_hint)) }
}
