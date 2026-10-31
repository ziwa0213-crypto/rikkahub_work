package me.rerere.rikkahub.ui.pages.group.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.data.model.GroupMemberRef
import me.rerere.rikkahub.data.model.withOrderedMembers
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.ui.components.FormItem
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import kotlin.uuid.Uuid

@Composable
fun GroupMemberPicker(
    settings: Settings,
    selected: Set<Uuid>,
    onChange: (Set<Uuid>) -> Unit,
    excluded: Set<Uuid> = emptySet(),
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 48.dp * 4 - 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        settings.groupMembers.filterNot { it.id in excluded }.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onChange(if (member.id in selected) selected - member.id else selected + member.id)
                }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UIAvatar(member.name, member.avatar, modifier = Modifier.size(36.dp), onClick = {
                    onChange(if (member.id in selected) selected - member.id else selected + member.id)
                })
                Column(Modifier.weight(1f)) {
                    Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(groupModelName(settings, member.chatModelId), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Checkbox(checked = member.id in selected, onCheckedChange = null)
            }
        }
    }
}

@Composable
fun NewGroupDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onCreate: (Group) -> Unit,
    onCreateMember: () -> Unit,
    initial: Group = remember { Group() },
    onUpdateTags: (List<Tag>) -> Unit = {},
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var selected by remember(initial.id) { mutableStateOf(initial.members.map { it.memberId }.toSet()) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.group_new)) },
        text = {
            Column(Modifier.heightIn(max = 48.dp * 8).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.group_select_members, selected.size), style = MaterialTheme.typography.titleMedium)
                if (settings.groupMembers.isEmpty()) {
                    TextButton(onClick = { onDismiss(); onCreateMember() }) { Text(stringResource(R.string.group_create_member_first)) }
                } else {
                    GroupMemberPicker(settings, selected, { selected = it })
                }
                GroupTagPicker(settings, tags, { tags = it }, onUpdateTags)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && selected.any { id -> settings.groupMembers.any { it.id == id } } && !saving,
                onClick = {
                    saving = true
                    onCreate(initial.copy(name = name.trim(), tags = tags).withOrderedMembers(
                        settings.groupMembers.filter { it.id in selected }.map { GroupMemberRef(it.id) }
                    ))
                }) { Text(stringResource(R.string.group_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun GroupRenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit,
    label: String = stringResource(R.string.group_rename),
    fieldLabel: String = stringResource(R.string.group_name)) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(fieldLabel) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.group_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NewMemberDialog(settings: Settings, initial: GroupMember, onDismiss: () -> Unit,
    onUpdateTags: (List<Tag>) -> Unit = {}, onSave: (GroupMember) -> Unit) {
    var member by remember(initial.id) { mutableStateOf(initial) }
    var showImport by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(if (settings.groupMembers.any { it.id == initial.id }) R.string.group_edit_member else R.string.group_new_member),
                style = MaterialTheme.typography.titleLarge)
            UIAvatar(member.name, member.avatar, modifier = Modifier.size(74.dp),
                onUpdate = { member = member.copy(avatar = it) })
            FormItem(label = { Text(stringResource(R.string.group_member_name)) }) {
                OutlinedTextField(member.name, { member = member.copy(name = it) }, Modifier.fillMaxWidth(), singleLine = true)
            }
            FormItem(label = { Text(stringResource(R.string.group_model)) },
                description = { Text(stringResource(R.string.assistant_page_chat_model_desc)) }) {
                ModelSelector(member.chatModelId, settings.providers, ModelType.CHAT,
                    onSelect = { member = member.copy(chatModelId = it.id) })
            }
            FormItem(label = { Text(stringResource(R.string.group_prompt)) }) {
                OutlinedTextField(member.systemPrompt, { member = member.copy(systemPrompt = it) },
                    Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
            }
            FormItem(label = { Text(stringResource(R.string.assistant_page_stream_output)) },
                description = { Text(stringResource(R.string.assistant_page_stream_output_desc)) }, tail = {
                Switch(member.streamOutput, { member = member.copy(streamOutput = it) })
            })
            MemberFloatParameter(
                label = stringResource(R.string.assistant_page_temperature),
                description = stringResource(R.string.assistant_page_temperature_warning),
                value = member.temperature, range = 0f..2f, hint = "0 - 2",
                onUpdate = { member = member.copy(temperature = it) },
            )
            MemberFloatParameter(
                label = stringResource(R.string.assistant_page_top_p),
                description = stringResource(R.string.assistant_page_top_p_warning),
                value = member.topP, range = 0f..1f, hint = "0 - 1",
                onUpdate = { member = member.copy(topP = it) },
            )
            FormItem(label = { Text(stringResource(R.string.assistant_page_context_message_limit)) },
                description = { Text(stringResource(R.string.assistant_page_context_message_limit_desc)) }) {
                var input by remember(member.id) { mutableStateOf(member.contextMessageLimit.toString()) }
                var focused by remember(member.id) { mutableStateOf(false) }
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = input, onValueChange = { text ->
                        if (text.all(Char::isDigit) && (text.isEmpty() || text.toIntOrNull() != null)) {
                            input = text
                            text.toIntOrNull()?.let { member = member.copy(contextMessageLimit = it) }
                        }
                    }, modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                        if (focused && !focus.isFocused) input = member.contextMessageLimit.toString()
                        focused = focus.isFocused
                    }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    supportingText = { Text(stringResource(R.string.assistant_page_context_message_limit_hint)) },
                )
                if (member.contextMessageLimit > 0) Text(
                    stringResource(R.string.assistant_page_context_message_limit_warning),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                )
            }
            FormItem(label = { Text(stringResource(R.string.assistant_page_max_tokens)) },
                description = { Text(stringResource(R.string.assistant_page_max_tokens_desc)) }) {
                var input by remember(member.id) { mutableStateOf(member.maxTokens?.toString().orEmpty()) }
                OutlinedTextField(
                    value = input, onValueChange = { text ->
                        input = text
                        if (text.isBlank()) member = member.copy(maxTokens = null)
                        else text.toIntOrNull()?.takeIf { it > 0 }?.let { member = member.copy(maxTokens = it) }
                    }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = input.isNotBlank() && (input.toIntOrNull()?.let { it <= 0 } != false),
                    placeholder = { Text(stringResource(R.string.assistant_page_max_tokens_no_limit)) },
                    supportingText = {
                        Text(member.maxTokens?.let { stringResource(R.string.assistant_page_max_tokens_limit, it) }
                            ?: stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                    },
                )
            }
            GroupTagPicker(settings, member.tags, { member = member.copy(tags = it) }, onUpdateTags,
                label = stringResource(R.string.group_member_tags))
            if (member.tags.none { id -> settings.groupTags.any { it.id == id } }) {
                Text(stringResource(R.string.group_member_tags_required), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showImport = true }) { Text(stringResource(R.string.group_import_assistant)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                TextButton(enabled = member.name.isNotBlank() && member.tags.any { id -> settings.groupTags.any { it.id == id } } && !saving, onClick = {
                    saving = true; onSave(member.copy(name = member.name.trim()))
                }) { Text(stringResource(R.string.group_save)) }
            }
        }
    }
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.group_import_assistant)) },
            text = {
                Column(Modifier.heightIn(max = 48.dp * 6).verticalScroll(rememberScrollState())) {
                    settings.assistants.forEach { assistant ->
                        ListItem(
                            headlineContent = { Text(assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }) },
                            leadingContent = {
                                UIAvatar(assistant.name, assistant.avatar, modifier = Modifier.size(36.dp), onClick = {
                                    member = member.importSnapshot(assistant)
                                    showImport = false
                                })
                            },
                            modifier = Modifier.clickable { member = member.importSnapshot(assistant); showImport = false },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showImport = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MemberFloatParameter(
    label: String, description: String, value: Float?, range: ClosedFloatingPointRange<Float>,
    hint: String, onUpdate: (Float?) -> Unit,
) {
    FormItem(label = { Text(label) }, description = { Text(description) }, tail = {
        Switch(checked = value != null, onCheckedChange = { onUpdate(if (it) 1.0f else null) })
    }) {
        if (value != null) {
            var input by remember { mutableStateOf(value.toString()) }
            val parsed = input.toFloatOrNull()
            OutlinedTextField(
                value = input, onValueChange = { text ->
                    input = text
                    text.toFloatOrNull()?.takeIf { it in range }?.let(onUpdate)
                }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = parsed == null || parsed !in range,
                supportingText = { Text(hint) },
            )
        }
    }
}
