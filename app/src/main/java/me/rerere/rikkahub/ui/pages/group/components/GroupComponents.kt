package me.rerere.rikkahub.ui.pages.group.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.UserGroup03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.data.model.Tag
import kotlin.uuid.Uuid

@Composable
fun GroupAvatar(
    group: Group,
    settings: Settings,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val members = group.members.mapNotNull { ref -> settings.groupMembers.find { it.id == ref.memberId } }.take(4)
    if (group.avatar != Avatar.Dummy) {
        UIAvatar(group.name, group.avatar, modifier = modifier, onClick = onClick)
    } else if (members.isEmpty()) {
        Box(
            modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
            contentAlignment = Alignment.Center,
        ) { Icon(HugeIcons.UserGroup03, null) }
    } else if (stacked) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
            members.forEach { member ->
                Surface(shape = CircleShape, border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {
                    UIAvatar(member.name, member.avatar, modifier = Modifier.size(28.dp), onClick = onClick)
                }
            }
        }
    } else {
        Column(modifier.clip(RoundedCornerShape(8.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            members.chunked(2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { member ->
                        UIAvatar(member.name, member.avatar, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onClick)
                    }
                    if (members.size > 2 && row.size == 1) Spacer(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun GroupTags(settings: Settings, ids: List<Uuid>) {
    val tags = settings.groupTags.filter { it.id in ids }
    if (tags.isEmpty()) return
    // CardGroup/ListItem asks its content for intrinsic sizes; lazy layouts cannot provide them.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.take(2).forEach { tag ->
            Surface(modifier = Modifier.weight(1f, fill = false), shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(tag.name, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tags.size > 2) {
            Text("+${tags.size - 2}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupTagPicker(settings: Settings, ids: List<Uuid>, onUpdate: (List<Uuid>) -> Unit,
    onUpdateTags: (List<Tag>) -> Unit = {}, label: String = stringResource(R.string.group_tags)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        TagsInput(value = ids, tags = settings.groupTags, onValueChange = { selected, tags ->
            onUpdateTags(tags)
            onUpdate(selected)
        })
    }
}

@Composable
fun GroupEmptyState(title: String, description: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(HugeIcons.UserGroup03, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

@Composable
fun groupModelName(settings: Settings, modelId: Uuid?): String = settings.providers.flatMap { it.models }
    .find { it.id == modelId }?.displayName ?: stringResource(R.string.group_model_unset)
