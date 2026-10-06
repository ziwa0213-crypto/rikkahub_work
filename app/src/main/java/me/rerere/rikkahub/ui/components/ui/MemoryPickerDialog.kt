package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryMigrationMode

data class MemoryPickerModeSwitch(
    val value: MemoryMigrationMode,
    val onValueChange: (MemoryMigrationMode) -> Unit,
)

@Composable
fun MemoryPickerDialog(
    show: Boolean,
    title: String,
    subtitle: String? = null,
    memories: List<AssistantMemory>,
    selectedIds: Set<Int>,
    onSelectedIdsChange: (Set<Int>) -> Unit,
    modeSwitch: MemoryPickerModeSwitch? = null,
    warning: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val allIds = memories.map { it.id }.toSet()
    val allSelected = memories.isNotEmpty() && memories.all { it.id in selectedIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                warning?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                modeSwitch?.let { switch ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.memory_picker_mode_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val modes = MemoryMigrationMode.entries
                            modes.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = switch.value == mode,
                                    onClick = { switch.onValueChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                ) {
                                    Text(
                                        stringResource(
                                            if (mode == MemoryMigrationMode.COPY) {
                                                R.string.memory_picker_mode_copy
                                            } else {
                                                R.string.memory_picker_mode_move
                                            }
                                        )
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(
                                if (switch.value == MemoryMigrationMode.COPY) {
                                    R.string.memory_picker_mode_copy_desc
                                } else {
                                    R.string.memory_picker_mode_move_desc
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (memories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memory_picker_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                    ) {
                        items(memories, key = { it.id }) { memory ->
                            val selected = memory.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = selected,
                                        role = Role.Checkbox,
                                        onValueChange = {
                                            onSelectedIdsChange(
                                                if (it) selectedIds + memory.id else selectedIds - memory.id
                                            )
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected, onCheckedChange = null)
                                Text(
                                    text = memory.content,
                                    modifier = Modifier.padding(start = 8.dp),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.memory_picker_selected_count,
                            selectedIds.intersect(allIds).size,
                            memories.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = {
                            onSelectedIdsChange(if (allSelected) allIds - selectedIds else allIds)
                        },
                    ) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.memory_picker_invert
                                else R.string.memory_picker_select_all
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
