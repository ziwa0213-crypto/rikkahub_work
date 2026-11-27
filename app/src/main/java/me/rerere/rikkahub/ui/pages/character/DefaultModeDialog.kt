package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Message02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.UiMode

@Composable
internal fun DefaultModeDialog(onConfirm: (UiMode) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(UiMode.CHAT) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.default_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.default_mode_desc))
                listOf(UiMode.CHAT, UiMode.ROLE).forEach { mode ->
                    Surface(
                        onClick = { selected = mode },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (selected == mode) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (mode == UiMode.CHAT) HugeIcons.Message02 else HugeIcons.LookTop, null, Modifier.size(24.dp))
                            Column {
                                Text(stringResource(if (mode == UiMode.CHAT) R.string.default_mode_work else R.string.default_mode_role), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (mode == UiMode.CHAT) R.string.default_mode_work_desc else R.string.default_mode_role_desc), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.confirm)) } },
    )
}
