package me.rerere.rikkahub.ui.pages.setting.locallm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.llamacpp.LlamaCppChatTemplates
import me.rerere.rikkahub.R
import me.rerere.ui.components.Select

@Composable
internal fun LocalChatTemplateEditor(model: Model, onChange: (Model) -> Unit) {
    val presets = LlamaCppChatTemplates.presets
    var mode by remember(model.id) { mutableStateOf(when {
        model.chatTemplate == null -> 0
        model.chatTemplate in presets.values -> 1
        else -> 2
    }) }
    var preset by remember(model.id) { mutableStateOf(presets.entries.find { it.value == model.chatTemplate }?.key ?: presets.keys.first()) }
    var custom by remember(model.id) { mutableStateOf(model.chatTemplate.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.local_llm_chat_template), style = MaterialTheme.typography.titleSmall)
        Select(options = listOf(0, 1, 2), selectedOption = mode, onOptionSelected = {
            mode = it
            onChange(model.copy(chatTemplate = when (it) { 0 -> null; 1 -> presets.getValue(preset); else -> custom }))
        }, optionToString = { stringResource(when (it) {
            0 -> R.string.local_llm_template_auto
            1 -> R.string.local_llm_template_preset
            else -> R.string.local_llm_template_custom
        }) })
        if (mode == 1) Select(options = presets.keys.toList(), selectedOption = preset, onOptionSelected = {
            preset = it; onChange(model.copy(chatTemplate = presets.getValue(it)))
        })
        if (mode == 2) {
            OutlinedTextField(value = custom, onValueChange = {
                custom = it; onChange(model.copy(chatTemplate = it))
            }, label = { Text("Jinja") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
            if (LlamaCppChatTemplates.isSuspicious(custom)) Text(stringResource(R.string.local_llm_template_suspicious),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
