package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.ShieldCheck
import me.rerere.hugeicons.stroke.ShieldOff
import me.rerere.hugeicons.stroke.ShieldQuestionMark
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ToolApprovalMode
import me.rerere.ui.components.ToggleSurface

@Composable
fun ToolApprovalButton(
    mode: ToolApprovalMode,
    onUpdateMode: (ToolApprovalMode) -> Unit,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ToolApprovalPicker(
            mode = mode,
            onDismissRequest = { showPicker = false },
            onUpdateMode = {
                onUpdateMode(it)
                showPicker = false
            },
        )
    }

    ToggleSurface(
        checked = mode != ToolApprovalMode.AllowAll,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = mode.icon(),
                contentDescription = stringResource(R.string.tool_approval_button_content_description),
                modifier = Modifier.size(24.dp),
                tint = if (mode == ToolApprovalMode.AllowAll) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (!onlyIcon) {
                Text(mode.label())
            }
        }
    }
}

@Composable
fun ToolApprovalPicker(
    mode: ToolApprovalMode,
    onDismissRequest: () -> Unit = {},
    onUpdateMode: (ToolApprovalMode) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.tool_approval_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.tool_approval_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            ToolApprovalMode.entries.forEach { option ->
                val selected = option == mode
                Surface(
                    onClick = { onUpdateMode(option) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (option == ToolApprovalMode.AllowAll) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label(),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = option.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = HugeIcons.CheckmarkCircle02,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ToolApprovalMode.icon(): ImageVector = when (this) {
    ToolApprovalMode.AskAll -> HugeIcons.ShieldQuestionMark
    ToolApprovalMode.Partial -> HugeIcons.ShieldCheck
    ToolApprovalMode.AllowAll -> HugeIcons.ShieldOff
}

@Composable
private fun ToolApprovalMode.label(): String = when (this) {
    ToolApprovalMode.AskAll -> stringResource(R.string.tool_approval_ask_all)
    ToolApprovalMode.Partial -> stringResource(R.string.tool_approval_partial)
    ToolApprovalMode.AllowAll -> stringResource(R.string.tool_approval_allow_all)
}

@Composable
private fun ToolApprovalMode.description(): String = when (this) {
    ToolApprovalMode.AskAll -> stringResource(R.string.tool_approval_ask_all_desc)
    ToolApprovalMode.Partial -> stringResource(R.string.tool_approval_partial_desc)
    ToolApprovalMode.AllowAll -> stringResource(R.string.tool_approval_allow_all_desc)
}
