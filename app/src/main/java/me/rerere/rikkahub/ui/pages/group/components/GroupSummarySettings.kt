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
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_GROUP_COMPRESS_PROMPT
import me.rerere.rikkahub.ui.theme.CustomColors

/** Manual summary options; automatic compression uses the global upstream settings. */
@Composable
internal fun GroupSummarySettings(group: Group, prompt: String, onUpdate: ((Group) -> Group) -> Unit,
    onUpdatePrompt: (String) -> Unit) {
    var promptDraft by remember(group.id) { mutableStateOf<String?>(null) }
    val localPrompt = stringResource(R.string.group_compress_prompt_template)
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp), colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.chat_page_compress_context), style = MaterialTheme.typography.titleMedium)
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
