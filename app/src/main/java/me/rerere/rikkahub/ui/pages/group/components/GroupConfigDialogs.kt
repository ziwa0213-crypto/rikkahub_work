package me.rerere.rikkahub.ui.pages.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.data.model.GroupMemberRef
import me.rerere.rikkahub.data.model.Tag
import me.rerere.ui.components.FormItem
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Composable
fun GroupInfoDialog(group: Group, settings: Settings, onDismiss: () -> Unit,
    onUpdateTags: (List<Tag>) -> Unit = {}, onSave: (Group) -> Unit) {
    var edited by remember(group.id) { mutableStateOf(group) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.group_info)) },
        text = {
            Column(Modifier.heightIn(max = 48.dp * 8).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                UIAvatar(edited.name, edited.avatar, modifier = Modifier.size(74.dp), onUpdate = { edited = edited.copy(avatar = it) })
                OutlinedTextField(edited.name, { edited = edited.copy(name = it) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.group_name)) }, singleLine = true)
                GroupTagPicker(settings, edited.tags, { edited = edited.copy(tags = it) }, onUpdateTags)
            }
        }, confirmButton = {
            TextButton(enabled = edited.name.isNotBlank(), onClick = { onSave(edited.copy(name = edited.name.trim())) }) {
                Text(stringResource(R.string.group_save))
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun AddGroupMembersDialog(group: Group, settings: Settings, onDismiss: () -> Unit,
    onCreateMember: () -> Unit, onAdd: (Set<Uuid>) -> Unit) {
    var ids by remember { mutableStateOf(emptySet<Uuid>()) }
    val excluded = group.members.map { it.memberId }.toSet()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.group_add_members)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (settings.groupMembers.none { it.id !in excluded }) {
                    Text(stringResource(R.string.group_all_members_added))
                    TextButton(onClick = onCreateMember) { Text(stringResource(R.string.group_new_member)) }
                } else GroupMemberPicker(settings, ids, { ids = it }, excluded)
            }
        }, confirmButton = { TextButton(enabled = ids.isNotEmpty(), onClick = { onAdd(ids) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun MemberConfigSheet(group: Group, member: GroupMember, ref: GroupMemberRef, onDismiss: () -> Unit,
    onUpdate: (GroupMemberRef, Int) -> Unit, onRemove: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }
    var nickname by remember(group.id, ref.memberId) { mutableStateOf(ref.nickname) }
    var avatarOverride by remember(group.id, ref.memberId, ref.avatar) { mutableStateOf(ref.avatar) }
    val latestRef by rememberUpdatedState(ref)
    var priority by remember(ref.priority) { mutableFloatStateOf(ref.priority.toFloat()) }
    var talkativeness by remember(ref.talkativeness) { mutableFloatStateOf(ref.talkativeness) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(member.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.group_member_settings, group.name), style = MaterialTheme.typography.bodySmall)
            FormItem(label = { Text(stringResource(R.string.group_nickname)) },
                description = { Text(stringResource(R.string.group_nickname_hint)) }) {
                OutlinedTextField(value = nickname, onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(member.name) })
            }
            FormItem(label = { Text(stringResource(R.string.group_avatar)) },
                description = { Text(stringResource(R.string.group_avatar_hint)) }) {
                UIAvatar(name = nickname.ifBlank { member.name }, value = avatarOverride ?: member.avatar,
                    modifier = Modifier.size(48.dp), onUpdate = { updated ->
                        avatarOverride = updated
                        onUpdate(latestRef.copy(avatar = updated), latestRef.priority)
                    })
            }
            FormItem(label = { Text(stringResource(R.string.group_priority)) }, tail = { Text((priority.roundToInt() + 1).toString()) }) {
                Slider(value = priority, onValueChange = { priority = it }, enabled = group.members.size > 1,
                    valueRange = 0f..(group.members.size - 1).coerceAtLeast(1).toFloat(),
                    steps = (group.members.size - 2).coerceAtLeast(0),
                    onValueChangeFinished = { onUpdate(ref, priority.roundToInt()) })
            }
            FormItem(label = { Text(stringResource(R.string.group_talkativeness)) }, tail = { Text("${(talkativeness * 100).roundToInt()}%") }) {
                Slider(value = talkativeness, onValueChange = { talkativeness = it }, valueRange = 0f..1f,
                    onValueChangeFinished = { onUpdate(ref.copy(talkativeness = talkativeness), ref.priority) })
            }
            FormItem(label = { Text(stringResource(R.string.group_enable_member)) }, tail = {
                Switch(ref.enabled, { onUpdate(ref.copy(enabled = it), ref.priority) })
            })
            TextButton(onClick = {
                onUpdate(latestRef.copy(nickname = nickname.trim(), avatar = avatarOverride), latestRef.priority)
                onDismiss()
            }) { Text(stringResource(R.string.group_save)) }
            TextButton(onClick = { confirmRemove = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.group_remove_member))
            }
        }
    }
    RikkaConfirmDialog(confirmRemove, stringResource(R.string.group_remove_member), stringResource(R.string.confirm),
        stringResource(R.string.cancel), onConfirm = { confirmRemove = false; onRemove() }, onDismiss = { confirmRemove = false }) {
        Text(stringResource(R.string.group_remove_member_hint))
    }
}
