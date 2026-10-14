package me.rerere.rikkahub.ui.pages.group.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.hugeicons.stroke.Setting07
import me.rerere.hugeicons.stroke.UserGroup03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.pages.chat.DateHeaderItem
import me.rerere.rikkahub.ui.pages.chat.PinnedHeader
import me.rerere.rikkahub.utils.toLocalString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal sealed interface GroupConversationListItem {
    data class DateHeader(val date: LocalDate) : GroupConversationListItem
    data object PinnedHeader : GroupConversationListItem
    data class Item(val conversation: Conversation) : GroupConversationListItem
}

internal fun buildGroupConversationListItems(conversations: List<Conversation>, zoneId: ZoneId = ZoneId.systemDefault()): List<GroupConversationListItem> = buildList {
    // Display sorting must never replace the library order saved by GroupPage.
    val sorted = conversations.sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.createAt })
    var pinnedHeaderAdded = false
    var lastDate: LocalDate? = null
    sorted.forEach { conversation ->
        if (conversation.isPinned) {
            if (!pinnedHeaderAdded) {
                add(GroupConversationListItem.PinnedHeader)
                pinnedHeaderAdded = true
            }
        } else {
            val date = conversation.createAt.atZone(zoneId).toLocalDate()
            if (date != lastDate) {
                add(GroupConversationListItem.DateHeader(date))
                lastDate = date
            }
        }
        add(GroupConversationListItem.Item(conversation))
    }
}

@Composable
fun GroupConversationList(
    settings: Settings,
    group: Group?,
    conversations: List<Conversation>,
    current: Conversation?,
    onSelect: (Conversation) -> Unit,
    onCreate: () -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = buildGroupConversationListItems(conversations)
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (group != null && conversations.isNotEmpty()) item {
            TextButton(onClick = onCreate) {
                Icon(HugeIcons.Add01, null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.group_new_chat))
            }
        }
        if (conversations.isEmpty()) item {
            GroupEmptyState(stringResource(if (group == null) R.string.group_empty_groups else R.string.group_no_conversations),
                stringResource(if (group == null) R.string.group_empty_groups_hint else R.string.group_chat_empty)) {
                TextButton(onClick = onCreate) {
                    Icon(HugeIcons.Add01, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (group == null) R.string.group_new else R.string.group_new_chat))
                }
            }
        }
        items(items, key = { item ->
            when (item) {
                is GroupConversationListItem.DateHeader -> "date_${item.date}"
                GroupConversationListItem.PinnedHeader -> "pinned_header"
                is GroupConversationListItem.Item -> item.conversation.id.toString()
            }
        }) { item ->
            when (item) {
                is GroupConversationListItem.DateHeader -> DateHeaderItem(dateLabel(item.date), Modifier.animateItem())
                GroupConversationListItem.PinnedHeader -> PinnedHeader(
                    modifier = Modifier.animateItem(),
                )
                is GroupConversationListItem.Item -> GroupConversationRow(
                    group = group, settings = settings, conversation = item.conversation, selected = current?.id == item.conversation.id,
                    onClick = { onSelect(item.conversation) }, onTogglePin = { onTogglePin(item.conversation) },
                    onRename = { onRename(item.conversation) },
                    onDelete = { onDelete(item.conversation) }, modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.chat_page_today)
        today.minusDays(1) -> stringResource(R.string.chat_page_yesterday)
        else -> date.toLocalString(date.year != today.year)
    }
}

@Composable
private fun GroupConversationRow(
    group: Group?,
    settings: Settings,
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val backgroundColor = if (selected) MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp) else Color.Transparent
    var showDropdownMenu by remember { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(50f)).combinedClickable(
        interactionSource = interactionSource, indication = LocalIndication.current,
        onClick = onClick, onLongClick = {
            focusManager.clearFocus(force = true)
            showDropdownMenu = true
        },
    ).background(backgroundColor)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (group != null) {
                GroupAvatar(group, settings, Modifier.size(28.dp), onClick = onClick)
                Spacer(Modifier.width(8.dp))
            }
            Text(conversation.title.ifBlank { stringResource(R.string.group_chat_new_conversation_title) },
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(conversation.isPinned) {
                Icon(HugeIcons.Pin, stringResource(R.string.pinned_chats), Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = showDropdownMenu, onDismissRequest = { showDropdownMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (conversation.isPinned) R.string.unpin_chat else R.string.pin_chat)) },
                    leadingIcon = { Icon(if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin, null) },
                    onClick = { onTogglePin(); showDropdownMenu = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_page_rename)) },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                    onClick = { onRename(); showDropdownMenu = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(HugeIcons.Delete01, null) },
                    onClick = { onDelete(); showDropdownMenu = false },
                )
            }
        }
    }
}

@Composable
fun GroupPicker(settings: Settings, current: Group?, onSelect: (Group) -> Unit, onCreate: () -> Unit, onEdit: (Group) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        selected = false, onClick = { if (current == null) onCreate() else showPicker = true },
        icon = { Icon(HugeIcons.UserGroup03, null) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current?.name ?: stringResource(R.string.group_empty_groups), modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) IconButton(onClick = { onEdit(current) }) {
                    GroupAvatar(current, settings, Modifier.size(36.dp), onClick = { onEdit(current) })
                } else Icon(HugeIcons.Add01, stringResource(R.string.group_new))
            }
        },
    )
    if (showPicker) ModalBottomSheet(
        onDismissRequest = { showPicker = false },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 48.dp * 8).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.group_mode_group), style = MaterialTheme.typography.titleLarge)
            settings.groups.forEach { group ->
                NavigationDrawerItem(selected = current?.id == group.id,
                    onClick = { showPicker = false; onSelect(group) },
                    icon = {
                        GroupAvatar(group, settings, Modifier.size(48.dp), onClick = {
                            showPicker = false
                            onSelect(group)
                        })
                    },
                    label = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    badge = { Text(stringResource(R.string.group_member_count, group.members.size)) })
            }
            TextButton(onClick = { showPicker = false; onCreate() }) { Text(stringResource(R.string.group_new)) }
        }
    }
}
