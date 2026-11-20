package me.rerere.rikkahub.ui.pages.setting.locallm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Modality
import me.rerere.llamacpp.FileRole
import me.rerere.llamacpp.InstallProgress
import me.rerere.llamacpp.InstalledLocalModel
import me.rerere.llamacpp.LlamaCppCatalogEntry
import me.rerere.llamacpp.LocalModelState
import me.rerere.rikkahub.R
import java.io.File
import java.util.Locale

internal val LlamaCppCatalogEntry.nameRes: Int get() = when (resource) {
    "lfm3_17b" -> R.string.local_llm_model_lfm3_17b_name
    "phi4mini" -> R.string.local_llm_model_phi4mini_name
    "qwen35_4b" -> R.string.local_llm_model_qwen35_4b_name
    "gemma4_e2b" -> R.string.local_llm_model_gemma4_e2b_name
    else -> R.string.local_llm_model_qwen35_9b_name
}
private val LlamaCppCatalogEntry.descRes: Int get() = when (resource) {
    "lfm3_17b" -> R.string.local_llm_model_lfm3_17b_desc
    "phi4mini" -> R.string.local_llm_model_phi4mini_desc
    "qwen35_4b" -> R.string.local_llm_model_qwen35_4b_desc
    "gemma4_e2b" -> R.string.local_llm_model_gemma4_e2b_desc
    else -> R.string.local_llm_model_qwen35_9b_desc
}
private val LlamaCppCatalogEntry.bodyRes: Int get() = when (resource) {
    "lfm3_17b" -> R.string.local_llm_model_lfm3_17b_body
    "phi4mini" -> R.string.local_llm_model_phi4mini_body
    "qwen35_4b" -> R.string.local_llm_model_qwen35_4b_body
    "gemma4_e2b" -> R.string.local_llm_model_gemma4_e2b_body
    else -> R.string.local_llm_model_qwen35_9b_body
}
internal fun formatSize(bytes: Long): String = String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)

@Composable
fun LocalModelSheet(
    entry: LlamaCppCatalogEntry?, installed: InstalledLocalModel?, filename: String,
    progress: InstallProgress?, busy: Boolean, onDismiss: () -> Unit, onInstall: () -> Unit,
    onCancel: () -> Unit, onDelete: () -> Unit, onProjector: () -> Unit,
    runtimeState: LocalModelState = LocalModelState.Idle, onLoad: () -> Unit = {}, onUnload: () -> Unit = {},
    onConfigure: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Expanded, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .66f).dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry?.let { stringResource(it.nameRes) } ?: filename, style = MaterialTheme.typography.headlineSmall)
                entry?.let {
                    Text(stringResource(it.descRes), style = MaterialTheme.typography.bodyMedium)
                    val specs = listOf(
                        R.string.local_llm_spec_params to it.params,
                        R.string.local_llm_spec_quant to it.quant,
                        R.string.local_llm_spec_size to formatSize(it.files.first().sizeBytes),
                        R.string.local_llm_spec_vision to (it.files.firstOrNull { file -> file.role == FileRole.MMPROJ }?.let { file -> formatSize(file.sizeBytes) }
                            ?: stringResource(R.string.local_llm_vision_none)),
                        R.string.local_llm_spec_mem to "${it.minMemGb} GB+",
                        R.string.local_llm_spec_ctx to "16K",
                    )
                    specs.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) -> Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                                    Text(value, style = MaterialTheme.typography.titleSmall)
                                }
                            } }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        it.modalities.forEach { modality -> AssistChip(onClick = {}, label = { Text(stringResource(when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                            Modality.AUDIO -> R.string.audio
                            Modality.VIDEO -> R.string.video
                        })) }) }
                    }
                    Text(stringResource(it.bodyRes), style = MaterialTheme.typography.bodyMedium)
                    Text("${stringResource(R.string.local_llm_license)} · ${it.license}", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { uri.openUri("https://huggingface.co/${it.repo}") }) { Text(stringResource(R.string.local_llm_source)) }
                } ?: Text(formatSize(installed?.let { File(it.path).length() } ?: 0))
                Text(stringResource(R.string.local_llm_context_hint), style = MaterialTheme.typography.bodySmall)
                if (runtimeState is LocalModelState.Ready && runtimeState.modelId == filename && runtimeState.gpuFallback) {
                    Text(stringResource(R.string.local_llm_gpu_fallback), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (runtimeState is LocalModelState.Ready && runtimeState.modelId == filename && !runtimeState.hasChatTemplate) {
                    Text(stringResource(R.string.local_llm_template_missing), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onConfigure) { Text(stringResource(R.string.local_llm_chat_template)) }
                }
                Text(stringResource(R.string.local_llm_model_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (installed != null) TextButton(onClick = onProjector, enabled = !busy) { Text(stringResource(R.string.local_llm_vision_add)) }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    runtimeState is LocalModelState.Ready && runtimeState.modelId == filename -> {
                        TextButton(onClick = onUnload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_llm_loaded_unload)) }
                        TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text(stringResource(R.string.local_llm_delete)) }
                    }
                    runtimeState is LocalModelState.Loading && runtimeState.modelId == filename -> {
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(localLoadingText(runtimeState)) }
                    }
                    runtimeState is LocalModelState.Failed && runtimeState.modelId == filename && installed != null -> {
                        Button(onClick = onLoad, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_llm_retry_load)) }
                        Text(localLoadFailure(runtimeState), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text(stringResource(R.string.local_llm_delete)) }
                    }
                    progress != null -> {
                        if (progress.total > 0) {
                            LinearProgressIndicator(progress = { (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${(progress.done * 100 / progress.total).coerceIn(0, 100)}% · ${formatSize(progress.done)} / ${formatSize(progress.total)}")
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("${stringResource(R.string.local_llm_installing)} ${formatSize(progress.done)}")
                        }
                        if (progress.projector) Text(stringResource(R.string.local_llm_projector_downloading))
                        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                    }
                    installed != null -> {
                        Button(onClick = onLoad, enabled = !busy && runtimeState !is LocalModelState.Loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_llm_load)) }
                        TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_llm_delete)) }
                    }
                    else -> Button(onClick = onInstall, enabled = !busy && entry != null, modifier = Modifier.fillMaxWidth()) {
                        Text("${stringResource(R.string.local_llm_install)} · ${entry?.files?.joinToString(" + ") { formatSize(it.sizeBytes) }.orEmpty()}")
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.local_llm_delete)) }, text = { Text(stringResource(R.string.local_llm_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
