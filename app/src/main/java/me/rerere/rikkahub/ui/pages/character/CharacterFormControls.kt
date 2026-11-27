package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.ui.components.RikkaConfirmDialog

internal fun CardGroupScope.characterTextField(label: Int, value: String, lines: Int = 1, update: (String) -> Unit) {
    formItem { OutlinedTextField(value, update, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, singleLine = lines == 1, minLines = lines, maxLines = maxOf(lines, 12)) }
}

internal fun CardGroupScope.characterNumber(label: Int, value: Int?, range: IntRange = 0..Int.MAX_VALUE,
    nullable: Boolean = false, allowZero: Boolean = false, update: (Int?) -> Unit) {
    formItem { CharacterNumberInput(label, value, range, nullable, allowZero, update) }
}

@Composable
private fun CharacterNumberInput(label: Int, value: Int?, range: IntRange, nullable: Boolean, allowZero: Boolean, update: (Int?) -> Unit) {
    var input by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val number = input.toIntOrNull()
    val valid = (nullable && input.isBlank()) || (number != null && (number in range || (allowZero && number == 0)))
    OutlinedTextField(input, { text ->
        input = text
        val next = text.toIntOrNull()
        if (text.isBlank() && nullable) update(null) else if (next != null && (next in range || (allowZero && next == 0))) update(next)
    }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid,
        supportingText = if (!valid) {{ Text(stringResource(R.string.character_numeric_invalid)) }} else null)
}

internal fun CardGroupScope.characterChoice(label: Int, selected: Int, labels: List<Int>, update: (Int) -> Unit) {
    item(headlineContent = { Text(stringResource(label)) }, trailingContent = {
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) { Text(stringResource(labels[selected.coerceIn(labels.indices)])) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, id -> DropdownMenuItem(text = { Text(stringResource(id)) }, onClick = {
                    expanded = false; update(index)
                }) }
            }
        }
    })
}

internal val characterRoleLabels = listOf(R.string.character_role_system, R.string.character_role_user, R.string.character_role_assistant)
internal val characterRoleValues = listOf("system", "user", "assistant")
internal val characterPositionLabels = listOf(R.string.character_position_0, R.string.character_position_1,
    R.string.character_position_2, R.string.character_position_3, R.string.character_position_4,
    R.string.character_position_5, R.string.character_position_6, R.string.character_position_7)

@Composable
internal fun CharacterChips(label: Int, values: List<String>, update: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, value -> InputChip(selected = false, onClick = { deleting = index },
                label = { Text(value) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(label)) })
            TextButton(enabled = input.isNotBlank(), onClick = { update(values + input.trim()); input = "" }) { Text(stringResource(R.string.add)) }
        }
    }
    RikkaConfirmDialog(show = deleting != null, title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete), dismissText = stringResource(R.string.cancel),
        onConfirm = { val index = deleting; if (index != null) update(values.filterIndexed { i, _ -> i != index }); deleting = null },
        onDismiss = { deleting = null }) { Text(values.getOrNull(deleting ?: -1).orEmpty()) }
}
