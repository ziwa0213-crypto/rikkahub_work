package me.rerere.rikkahub.ui.pages.setting.components

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import org.koin.compose.koinInject

@Composable
internal fun ProviderConfigureDeepSeekWeb(
    provider: ProviderSetting.DeepSeekWeb,
    onEdit: (ProviderSetting.DeepSeekWeb) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val manager = koinInject<ProviderManager>()
    var showLogin by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(deepSeekModels.first()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    provider.description()

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_status_title)) }) {
        item(
            headlineContent = { Text(if (provider.token.isBlank()) stringResource(R.string.deepseek_web_not_logged_in) else stringResource(R.string.deepseek_web_logged_in)) },
            supportingContent = { Text(provider.accountHint.ifBlank { stringResource(R.string.deepseek_web_credential_hint) }) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.deepseek_web_login_title)) },
            supportingContent = { Text(stringResource(R.string.deepseek_web_login_hint)) },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showLogin = true }) { Text(stringResource(R.string.deepseek_web_login_browser)) }
                    if (provider.token.isNotBlank()) {
                        TextButton(onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            onEdit(provider.copy(token = "", cookie = "", fingerprintHeaders = emptyMap(), accountHint = "", capturedAt = 0L))
                        }) { Text(stringResource(R.string.deepseek_web_logout)) }
                    }
                }
            },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_manual_title)) }) {
        item(
            headlineContent = {
                OutlinedTextField(
                    value = provider.token,
                    onValueChange = { onEdit(provider.copy(token = it.trim())) },
                    label = { Text(stringResource(R.string.deepseek_web_token)) },
                    supportingText = { Text(stringResource(R.string.deepseek_web_token_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        item(
            headlineContent = {
                OutlinedTextField(
                    value = provider.cookie,
                    onValueChange = { onEdit(provider.copy(cookie = it.trim())) },
                    label = { Text(stringResource(R.string.deepseek_web_cookie)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            },
        )
        item(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
                    Switch(checked = provider.enabled, onCheckedChange = { onEdit(provider.copy(enabled = it)) })
                }
            },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_throttle_title)) }) {
        item(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = provider.throttleMinMs.toString(),
                        onValueChange = { onEdit(provider.copy(throttleMinMs = it.toIntOrNull()?.coerceIn(1000, 15000) ?: provider.throttleMinMs)) },
                        label = { Text(stringResource(R.string.deepseek_web_throttle_min)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = provider.throttleMaxMs.toString(),
                        onValueChange = { onEdit(provider.copy(throttleMaxMs = it.toIntOrNull()?.coerceIn(1000, 15000) ?: provider.throttleMaxMs)) },
                        label = { Text(stringResource(R.string.deepseek_web_throttle_max)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
            supportingContent = { Text(stringResource(R.string.deepseek_web_throttle_hint)) },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_test_title)) }) {
        item(
            headlineContent = {
                Select(
                    options = deepSeekModels,
                    selectedOption = selectedModel,
                    onOptionSelected = { selectedModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { it.displayName },
                )
            },
            supportingContent = { Text(stringResource(R.string.deepseek_web_test_hint)) },
        )
        item(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !testing && provider.token.isNotBlank(),
                        onClick = {
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = runCatching {
                                    manager.getProviderByType(provider).generateText(
                                        provider,
                                        listOf(UIMessage.user("请只回复：连接成功")),
                                        TextGenerationParams(model = selectedModel),
                                    ).message.toText()
                                }.getOrElse { it.message ?: "测试失败" }
                                testing = false
                            }
                        },
                    ) { Text(stringResource(R.string.deepseek_web_test_button)) }
                    testResult?.let { Text(it, modifier = Modifier.weight(1f).then(Modifier), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.deepseek_web_limits),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (showLogin) {
        DeepSeekWebLogin(
            onCaptured = { token, cookie, headers ->
                onEdit(provider.copy(token = token, cookie = cookie, fingerprintHeaders = headers, capturedAt = System.currentTimeMillis()))
            },
            onDismiss = { showLogin = false },
        )
    }
}

private val deepSeekModels = listOf(
    Model("deepseek-web", "快速模式", type = ModelType.CHAT, abilities = listOf(ModelAbility.TOOL)),
    Model("deepseek-web-thinking", "思考模式", type = ModelType.CHAT, abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING)),
)
