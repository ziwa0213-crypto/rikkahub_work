package me.rerere.rikkahub.ui.pages.setting.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.LoadingAnimationConfig
import me.rerere.ai.provider.LoadingAnimationMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.LoadingAnimationIndicator
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject

private val PresetIds = listOf(
    "material",
    "brand_openai",
    "brand_claude",
    "brand_gemini",
    "brand_deepseek",
    "brand_generic",
)

@Composable
fun SettingProviderLoadingAnimation(
    provider: ProviderSetting,
    config: LoadingAnimationConfig,
    onEdit: (LoadingAnimationConfig) -> Unit,
) {
    val settings = LocalSettings.current
    val settingsStore: SettingsStore = koinInject()
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var hintAction by remember { mutableStateOf(HintAction.PICK_IMAGE) }
    var dontShowAgain by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    fun markHintDismissedIfNeeded() {
        if (dontShowAgain) {
            scope.launch {
                settingsStore.update { it.copy(loadingAnimationHintDismissed = true) }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            filesManager.createChatFilesByContents(listOf(selectedUri))
                .firstOrNull()
                ?.let { localUri ->
                    onEdit(
                        config.copy(
                            mode = LoadingAnimationMode.CUSTOM,
                            customUri = localUri.toString(),
                        )
                    )
                }
        }
    }

    fun openImagePicker() {
        if (settings.loadingAnimationHintDismissed) {
            imagePickerLauncher.launch("image/*")
        } else {
            dontShowAgain = false
            hintAction = HintAction.PICK_IMAGE
            showHint = true
        }
    }

    fun openUrlEditor() {
        if (settings.loadingAnimationHintDismissed) {
            urlInput = config.customUri.orEmpty()
            showUrlInput = true
        } else {
            dontShowAgain = false
            hintAction = HintAction.EDIT_URL
            showHint = true
        }
    }

    val previewProvider = remember(provider, config) {
        provider.copyProvider(loadingAnimation = config)
    }

    FormItem(
        label = {
            Text(stringResource(R.string.setting_provider_page_loading_animation_title))
        },
        description = {
            Text(stringResource(R.string.setting_provider_page_loading_animation_desc))
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.loading_animation_effective, effectiveLabel(previewProvider)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            LoadingAnimationIndicator(
                modifier = Modifier.size(32.dp),
                provider = previewProvider,
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeSelector(config = config, onEdit = onEdit)

                when (config.mode) {
                    LoadingAnimationMode.PRESET -> PresetSelector(
                        config = config,
                        provider = provider,
                        onEdit = onEdit,
                    )

                    LoadingAnimationMode.CUSTOM -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (config.customUri.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.loading_animation_custom_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .padding(8.dp),
                                )
                            } else {
                                LoadingAnimationIndicator(
                                    modifier = Modifier.size(72.dp),
                                    provider = previewProvider,
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                OutlinedButton(
                                    onClick = ::openImagePicker,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.loading_animation_custom_pick))
                                }
                                OutlinedButton(
                                    onClick = ::openUrlEditor,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.loading_animation_custom_url))
                                }
                                if (!config.customUri.isNullOrBlank()) {
                                    TextButton(
                                        onClick = {
                                            onEdit(config.copy(customUri = null))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.loading_animation_custom_remove))
                                    }
                                }
                            }
                        }
                        if (settings.loadingAnimationHintDismissed) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsStore.update { it.copy(loadingAnimationHintDismissed = false) }
                                    }
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_provider_page_loading_animation_reset_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    LoadingAnimationMode.AUTO -> Unit
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = showHint,
        title = stringResource(R.string.setting_provider_page_loading_animation_hint_title),
        confirmText = stringResource(R.string.setting_provider_page_loading_animation_hint_continue),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showHint = false
            markHintDismissedIfNeeded()
            when (hintAction) {
                HintAction.PICK_IMAGE -> imagePickerLauncher.launch("image/*")
                HintAction.EDIT_URL -> {
                    urlInput = config.customUri.orEmpty()
                    showUrlInput = true
                }
            }
        },
        onDismiss = {
            showHint = false
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setting_provider_page_loading_animation_hint_desc))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                    )
                    Text(stringResource(R.string.setting_provider_page_loading_animation_hint_dont_show_again))
                }
            }
        },
    )

    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { showUrlInput = false },
            title = { Text(stringResource(R.string.loading_animation_custom_url)) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(R.string.loading_animation_custom_url_input)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = urlInput.trim()
                        if (value.isNotEmpty()) {
                            onEdit(
                                config.copy(
                                    mode = LoadingAnimationMode.CUSTOM,
                                    customUri = value,
                                )
                            )
                            showUrlInput = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInput = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private enum class HintAction {
    PICK_IMAGE,
    EDIT_URL,
}

@Composable
private fun ModeSelector(
    config: LoadingAnimationConfig,
    onEdit: (LoadingAnimationConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.loading_animation_mode_title),
            style = MaterialTheme.typography.labelMedium,
        )
        LoadingAnimationMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEdit(
                            config.copy(
                                mode = mode,
                                presetId = if (mode == LoadingAnimationMode.PRESET) {
                                    config.presetId ?: "material"
                                } else {
                                    config.presetId
                                },
                            )
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = config.mode == mode,
                    onClick = {
                        onEdit(
                            config.copy(
                                mode = mode,
                                presetId = if (mode == LoadingAnimationMode.PRESET) {
                                    config.presetId ?: "material"
                                } else {
                                    config.presetId
                                },
                            )
                        )
                    },
                )
                Text(
                    text = stringResource(
                        when (mode) {
                            LoadingAnimationMode.AUTO -> R.string.loading_animation_mode_auto
                            LoadingAnimationMode.PRESET -> R.string.loading_animation_mode_preset
                            LoadingAnimationMode.CUSTOM -> R.string.loading_animation_mode_custom
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun PresetSelector(
    config: LoadingAnimationConfig,
    provider: ProviderSetting,
    onEdit: (LoadingAnimationConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PresetIds.forEach { presetId ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEdit(config.copy(presetId = presetId))
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = config.presetId == presetId,
                    onClick = { onEdit(config.copy(presetId = presetId)) },
                )
                Text(stringResource(presetNameResource(presetId)))
                LoadingAnimationIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                    provider = provider.copyProvider(
                        loadingAnimation = LoadingAnimationConfig(
                            mode = LoadingAnimationMode.PRESET,
                            presetId = presetId,
                        )
                    ),
                )
            }
        }
    }
}

private fun presetNameResource(presetId: String): Int = when (presetId) {
    "material" -> R.string.loading_animation_preset_material
    "brand_openai" -> R.string.loading_animation_preset_brand_openai
    "brand_claude" -> R.string.loading_animation_preset_brand_claude
    "brand_gemini" -> R.string.loading_animation_preset_brand_gemini
    "brand_deepseek" -> R.string.loading_animation_preset_brand_deepseek
    else -> R.string.loading_animation_preset_brand_generic
}

@Composable
private fun effectiveLabel(provider: ProviderSetting): String {
    val resolved = me.rerere.rikkahub.ui.components.ui.resolveLoadingAnimation(provider)
    return when (resolved) {
        is me.rerere.rikkahub.ui.components.ui.ResolvedLoadingAnimation.Custom ->
            stringResource(R.string.loading_animation_mode_custom)

        is me.rerere.rikkahub.ui.components.ui.ResolvedLoadingAnimation.Preset ->
            stringResource(presetNameResource(resolved.presetId))

        is me.rerere.rikkahub.ui.components.ui.ResolvedLoadingAnimation.Brand -> when (resolved.iconFile) {
            "openai.svg" -> stringResource(R.string.loading_animation_preset_brand_openai)
            "claude-color.svg" -> stringResource(R.string.loading_animation_preset_brand_claude)
            "gemini-color.svg" -> stringResource(R.string.loading_animation_preset_brand_gemini)
            "deepseek-color.svg" -> stringResource(R.string.loading_animation_preset_brand_deepseek)
            else -> stringResource(R.string.loading_animation_preset_brand_generic)
        }

        me.rerere.rikkahub.ui.components.ui.ResolvedLoadingAnimation.Material ->
            stringResource(R.string.loading_animation_preset_material)
    }
}
