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
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.compressionThreshold
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_GROUP_COMPRESS_PROMPT
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.theme.CustomColors
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
internal fun GroupCompressionSettings(group: Group, prompt: String, onUpdate: ((Group) -> Group) -> Unit,
    onUpdatePrompt: (String) -> Unit) {
    var threshold by remember(group.compressThresholdRatio) { mutableFloatStateOf(group.compressionThreshold()) }
    var promptDraft by remember(group.id) { mutableStateOf<String?>(null) }
    val localPrompt = stringResource(R.string.group_compress_prompt_template)
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp), colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.chat_page_compress_context), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.group_compress_auto), Modifier.weight(1f))
                Switch(group.enableAutoCompress, { enabled -> onUpdate { it.copy(enableAutoCompress = enabled) } })
            }
            Text(stringResource(R.string.group_compress_threshold_label) + " ${(threshold * 100).roundToInt()}%")
            Slider(threshold, { threshold = it }, valueRange = 0.30f..0.95f, steps = 12,
                onValueChangeFinished = { onUpdate { it.copy(compressThresholdRatio = threshold) } })
            Text(stringResource(R.string.group_compress_threshold_hint, NumberFormat.getIntegerInstance().format(
                ((group.compressContextLength.takeIf { it > 0 } ?: 128_000) * threshold).toLong())),
                style = MaterialTheme.typography.bodySmall)
            OutlinedNumberInput(group.compressContextLength,
                { value -> onUpdate { it.copy(compressContextLength = value.coerceAtLeast(0)) } },
                label = stringResource(R.string.group_compress_context_length), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.group_compress_by_count), Modifier.weight(1f))
                Switch(group.compressByMessageCount, { enabled -> onUpdate { it.copy(compressByMessageCount = enabled) } })
            }
            OutlinedNumberInput(group.compressMessageCount,
                { value -> onUpdate { it.copy(compressMessageCount = value.coerceAtLeast(1)) } },
                label = stringResource(R.string.group_compress_message_threshold), modifier = Modifier.fillMaxWidth())
            OutlinedNumberInput(group.compressKeepRecent,
                { value -> onUpdate { it.copy(compressKeepRecent = value.coerceAtLeast(0)) } },
                label = stringResource(R.string.chat_page_compress_keep_recent), modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.chat_page_compress_target_tokens), style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(500, 1000, 2000, 4000).forEachIndexed { index, tokens ->
                    SegmentedButton(group.compressTargetTokens == tokens,
                        { onUpdate { it.copy(compressTargetTokens = tokens) } }, SegmentedButtonDefaults.itemShape(index, 4)) { Text("$tokens") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.group_compress_tiered), Modifier.weight(1f))
                Switch(group.enableTieredCompress, { enabled -> onUpdate { it.copy(enableTieredCompress = enabled) } })
            }
            TextButton(onClick = { promptDraft = if (prompt == DEFAULT_GROUP_COMPRESS_PROMPT) localPrompt else prompt }) {
                Text(stringResource(R.string.group_compress_edit_prompt))
            }
        }
    }
    if (promptDraft != null) AlertDialog(onDismissRequest = { promptDraft = null },
        title = { Text(stringResource(R.string.group_compress_edit_prompt)) },
        text = { OutlinedTextField(promptDraft.orEmpty(), { promptDraft = it },
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), minLines = 5) },
        confirmButton = { TextButton(onClick = { onUpdatePrompt(promptDraft.orEmpty()); promptDraft = null }) { Text(stringResource(R.string.group_save)) } },
        dismissButton = { TextButton(onClick = { promptDraft = null }) { Text(stringResource(R.string.cancel)) } })
}
