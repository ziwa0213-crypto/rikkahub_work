package me.rerere.rikkahub.ui.pages.setting.locallm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.llamacpp.LlamaCppCatalog
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.utils.ErrorText
import org.koin.compose.koinInject

@Composable
fun LocalModelList(vm: SettingLocalLlmViewModel = koinInject()) {
    val runtimeState by vm.runtimeState.collectAsStateWithLifecycle()
    val models by vm.installer.models.collectAsStateWithLifecycle()
    val progress by vm.installer.progress.collectAsStateWithLifecycle()
    val interrupted by vm.installer.interrupted.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val warning by vm.architectureWarning.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var associate by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, associate)
        associate = null
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.local_llm_title)) }, navigationIcon = { BackButton() })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.local_llm_hint)) }
            item {
                Text(stringResource(R.string.local_llm_section_download))
                CardGroup {
                    LlamaCppCatalog.ENTRIES.forEach { entry ->
                        item(onClick = { selected = entry.filename },
                            headlineContent = { Text(stringResource(entry.nameRes)) },
                            supportingContent = { Text("${formatSize(entry.files.sumOf { it.sizeBytes })} · ${entry.quant}") },
                            trailingContent = { if (models.any { it.name == entry.filename }) Text(stringResource(R.string.local_llm_installed)) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.local_llm_section_installed))
                if (models.isEmpty()) Text(stringResource(R.string.local_llm_empty))
                else CardGroup {
                    models.forEach { model -> item(onClick = { selected = model.name },
                        headlineContent = { Text(LlamaCppCatalog.ENTRIES.find { it.filename == model.name }?.let { stringResource(it.nameRes) } ?: model.name) },
                        supportingContent = if (interrupted == model.path) { { Text(stringResource(R.string.local_llm_load_interrupted)) } } else null)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { import.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.local_llm_import))
                }
                if (busy && progress.isEmpty() && !warning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.local_llm_busy))
                    TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
    selected?.let { name ->
        LocalModelSheet(
            entry = LlamaCppCatalog.ENTRIES.find { it.filename == name },
            installed = models.find { it.name == name }, filename = name, progress = progress[name], busy = busy,
            onDismiss = { selected = null }, onInstall = { LlamaCppCatalog.ENTRIES.find { it.filename == name }?.let(vm::install) },
            onCancel = vm::cancel, onDelete = { vm.delete(name); selected = null },
            onProjector = { associate = name; import.launch(arrayOf("application/octet-stream", "*/*")) },
            runtimeState = runtimeState, onLoad = { vm.load(name) }, onUnload = vm::unload,
            onConfigure = {
                settings.providers.filterIsInstance<ProviderSetting.LlamaCppLocal>()
                    .find { provider -> provider.models.any { it.modelId == name } }?.let { provider ->
                        selected = null
                        nav.navigate(Screen.SettingProviderDetail(provider.id.toString(), tab = "models"))
                    }
            },
        )
    }
    LocalMemoryConfirmation(vm)
    if (error != null) AlertDialog(onDismissRequest = vm::dismissError,
        title = { Text(stringResource(R.string.local_llm_title)) },
        text = { Text(ErrorText.resolve(context, requireNotNull(error)) ?: context.getString(R.string.local_llm_error_bad_file)) },
        confirmButton = { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.confirm)) } })
    if (warning || pending?.needsWarning == true) AlertDialog(
        onDismissRequest = { if (warning) vm.approveDownload(false) else vm.discard() },
        text = { Text(stringResource(R.string.local_llm_arch_warning)) },
        confirmButton = { TextButton(onClick = { if (warning) vm.approveDownload(true) else vm.commit() }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { if (warning) vm.approveDownload(false) else vm.discard() }) { Text(stringResource(R.string.cancel)) } },
    )
    if (pending?.projector == true) AlertDialog(
        onDismissRequest = vm::discard,
        title = { Text(stringResource(R.string.local_llm_projector_choose)) },
        text = {
            Column {
                if (models.isEmpty()) Text(stringResource(R.string.local_llm_projector_first))
                models.forEach { item -> TextButton(onClick = { vm.commit(item.name) }) { Text(item.name) } }
            }
        },
        confirmButton = { TextButton(onClick = vm::discard) { Text(stringResource(R.string.cancel)) } },
    )
}
