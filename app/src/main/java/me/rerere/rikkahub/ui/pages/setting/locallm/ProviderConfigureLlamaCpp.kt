package me.rerere.rikkahub.ui.pages.setting.locallm

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppInstall
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.compose.koinInject

@Composable
fun ProviderConfigureLlamaCpp(provider: ProviderSetting.LlamaCppLocal, onEdit: (ProviderSetting) -> Unit) {
    val installer = koinInject<LlamaCppInstall>()
    val models by installer.models.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    CardGroup {
        formItem(headlineContent = { Text(stringResource(R.string.setting_provider_page_name)) }) {
            OutlinedTextField(provider.name, { onEdit(provider.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true)
        }
        formItem(headlineContent = { Text(stringResource(R.string.local_llm_download_base)) }) {
            OutlinedTextField(provider.downloadBase, { onEdit(provider.copy(downloadBase = it.trim())) }, Modifier.fillMaxWidth(),
                singleLine = true, isError = provider.downloadBase.toHttpUrlOrNull() == null,
                supportingText = { Text(stringResource(R.string.local_llm_download_base_hint)) })
        }
        item(onClick = { nav.navigate(Screen.SettingLocalLlm) },
            headlineContent = { Text(stringResource(R.string.local_llm_title)) },
            trailingContent = { Text(stringResource(R.string.local_llm_installed_count, models.size)) })
        item(onClick = { nav.navigate(Screen.SettingProviderDetail(provider.id.toString(), tab = "models")) },
            headlineContent = { Text(stringResource(R.string.local_llm_model_config)) },
            supportingContent = { Text(stringResource(R.string.local_llm_model_config_desc)) })
    }
}
