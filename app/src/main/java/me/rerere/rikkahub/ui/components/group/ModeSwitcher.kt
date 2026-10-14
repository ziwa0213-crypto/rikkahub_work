package me.rerere.rikkahub.ui.components.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.UserGroup03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.UiMode

@Composable
fun ModeSwitcher(mode: UiMode, onSelect: (UiMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Mode is conveyed by the icon and label, not by a selected background.
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 28.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (mode == UiMode.GROUP) HugeIcons.UserGroup03 else HugeIcons.Message02, null, Modifier.size(14.dp))
                Text(stringResource(if (mode == UiMode.GROUP) R.string.group_mode_group else R.string.group_mode_chat),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UiMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (option == UiMode.GROUP) R.string.group_mode_group else R.string.group_mode_chat)) },
                    leadingIcon = { Icon(if (option == UiMode.GROUP) HugeIcons.UserGroup03 else HugeIcons.Message02, null) },
                    trailingIcon = { if (option == mode) Icon(HugeIcons.Tick01, null) },
                    onClick = { expanded = false; if (option != mode) onSelect(option) },
                )
            }
        }
    }
}
