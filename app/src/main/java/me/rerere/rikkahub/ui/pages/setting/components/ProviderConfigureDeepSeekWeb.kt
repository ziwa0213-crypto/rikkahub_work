package me.rerere.rikkahub.ui.pages.setting.components

import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebModels
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.CalendarAdd01
import me.rerere.hugeicons.stroke.CalendarRemove01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.ClipboardPaste
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Cookie
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Login03
import me.rerere.hugeicons.stroke.Plug01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Shield01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Terminal
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.DeepSeekWebRequestParser
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.switchItem
import org.koin.compose.koinInject
import me.rerere.ui.components.Select

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
    var paste by remember { mutableStateOf("") }
    var pasteResult by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var showCustomWarning by remember { mutableStateOf(false) }
    val testSuccessText = stringResource(R.string.deepseek_web_test_success)
    val pasteEmptyText = stringResource(R.string.deepseek_web_paste_empty)
    val pasteNoAuthText = stringResource(R.string.deepseek_web_paste_no_auth)
    val pasteOkFormat = stringResource(R.string.deepseek_web_paste_ok)

    provider.description()

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_status_title)) }) {
        item(
            leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
            headlineContent = { Text(if (provider.token.isBlank()) stringResource(R.string.deepseek_web_not_logged_in) else stringResource(R.string.deepseek_web_logged_in)) },
            supportingContent = { Text(provider.accountHint.ifBlank { stringResource(R.string.deepseek_web_credential_hint) }) },
        )
        item(
            leadingContent = { CardGroupIcon(HugeIcons.Login03) },
            headlineContent = { Text(stringResource(R.string.deepseek_web_login_title)) },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.deepseek_web_login_hint))
                    TextButton(
                        onClick = { showLogin = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.deepseek_web_login_browser))
                    }
                    if (provider.token.isNotBlank()) {
                        TextButton(
                            onClick = {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                onEdit(provider.copy(token = "", cookie = "", fingerprintHeaders = emptyMap(), accountHint = "", capturedAt = 0L))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.deepseek_web_logout))
                        }
                    }
                }
            },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_manual_title)) }) {
        item(
            leadingContent = { CardGroupIcon(HugeIcons.ClipboardPaste) },
            headlineContent = {
                OutlinedTextField(
                    value = paste,
                    onValueChange = { paste = it; pasteResult = null },
                    label = { Text(stringResource(R.string.deepseek_web_paste_title)) },
                    placeholder = { Text(stringResource(R.string.deepseek_web_paste_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            supportingContent = {
                TextButton(
                    onClick = {
                        val parsed = DeepSeekWebRequestParser.parse(paste)
                        pasteResult = when {
                            !parsed.authorizationFound && parsed.isEmpty ->
                                pasteEmptyText
                            !parsed.authorizationFound || parsed.token.isBlank() ->
                                pasteNoAuthText
                            else -> {
                                onEdit(
                                    provider.copy(
                                        token = parsed.token,
                                        cookie = parsed.cookie,
                                        fingerprintHeaders = parsed.headers,
                                        capturedAt = System.currentTimeMillis(),
                                    ),
                                )
                                pasteOkFormat.format(
                                    if (parsed.token.isNotBlank()) "✓" else "—",
                                    if (parsed.cookie.isNotBlank()) "✓" else "—",
                                    parsed.headers.size,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.deepseek_web_paste_import))
                }
            },
        )
        pasteResult?.let { result ->
            item(
                leadingContent = { CardGroupIcon(HugeIcons.Search01) },
                headlineContent = { Text(result) },
            )
        }
        item(
            leadingContent = { CardGroupIcon(HugeIcons.Brain02) },
            headlineContent = { Text(stringResource(R.string.deepseek_web_manual_steps)) },
        )
        item(
            onClick = { advanced = !advanced },
            leadingContent = { CardGroupIcon(HugeIcons.Settings03) },
            headlineContent = { Text(stringResource(R.string.deepseek_web_manual_advanced)) },
            trailingContent = {
                IconButton(onClick = { advanced = !advanced }) {
                    Icon(
                        imageVector = if (advanced) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                    )
                }
            },
            supportingContent = {
                AnimatedVisibility(visible = advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = provider.token,
                            onValueChange = { onEdit(provider.copy(token = it.trim())) },
                            label = { Text(stringResource(R.string.deepseek_web_token)) },
                            supportingText = { Text(stringResource(R.string.deepseek_web_token_hint)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = provider.cookie,
                            onValueChange = { onEdit(provider.copy(cookie = it.trim())) },
                            label = { Text(stringResource(R.string.deepseek_web_cookie)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            },
        )
        switchItem(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
            leadingContent = { CardGroupIcon(HugeIcons.Settings03) },
            headlineContent = { Text(stringResource(R.string.setting_provider_page_enable)) },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_throttle_title)) }) {
        item(
            leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
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
            leadingContent = { CardGroupIcon(HugeIcons.Brain02) },
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
            leadingContent = { CardGroupIcon(HugeIcons.Search01) },
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                                    )
                                    testSuccessText
                                }.getOrElse { it.message ?: "测试失败" }
                                testing = false
                            }
                        },
                    ) { Text(stringResource(R.string.deepseek_web_test_button), maxLines = 1) }
                    Spacer(modifier = Modifier.width(8.dp))
                    testResult?.let {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.End,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }

    CardGroup(title = { Text(stringResource(R.string.deepseek_web_custom_title)) }) {
        switchItem(
            checked = provider.unlock.customEnabled,
            onCheckedChange = { checked ->
                if (checked) showCustomWarning = true
                else onEdit(provider.copy(unlock = provider.unlock.copy(customEnabled = false)))
            },
            leadingContent = { CardGroupIcon(HugeIcons.Shield01) },
            headlineContent = { Text(stringResource(R.string.deepseek_web_custom_toggle)) },
            supportingContent = { Text(stringResource(R.string.deepseek_web_custom_toggle_desc)) },
        )
        if (provider.unlock.customEnabled) {
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_calendar_create, HugeIcons.CalendarAdd01, provider.unlock.calendarCreate) {
                onEdit(provider.copy(unlock = provider.unlock.copy(calendarCreate = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_calendar_delete, HugeIcons.CalendarRemove01, provider.unlock.calendarDelete) {
                onEdit(provider.copy(unlock = provider.unlock.copy(calendarDelete = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_memory_write, HugeIcons.Brain02, provider.unlock.memoryWrite) {
                onEdit(provider.copy(unlock = provider.unlock.copy(memoryWrite = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_workspace_write, HugeIcons.FileEdit, provider.unlock.workspaceWrite) {
                onEdit(provider.copy(unlock = provider.unlock.copy(workspaceWrite = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_workspace_shell, HugeIcons.Terminal, provider.unlock.workspaceShell) {
                onEdit(provider.copy(unlock = provider.unlock.copy(workspaceShell = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_eval_javascript, HugeIcons.Code, provider.unlock.evalJavascript) {
                onEdit(provider.copy(unlock = provider.unlock.copy(evalJavascript = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_use_skill, HugeIcons.Puzzle, provider.unlock.useSkill) {
                onEdit(provider.copy(unlock = provider.unlock.copy(useSkill = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_mcp, HugeIcons.Plug01, provider.unlock.mcp) {
                onEdit(provider.copy(unlock = provider.unlock.copy(mcp = it)))
            }
            deepSeekWebUnlockSwitch(R.string.deepseek_web_unlock_clipboard_write, HugeIcons.Clipboard, provider.unlock.clipboardWrite) {
                onEdit(provider.copy(unlock = provider.unlock.copy(clipboardWrite = it)))
            }
        }
    }

    CardGroup(title = { Text(stringResource(R.string.provider_dsweb_limits_title)) }) {
        if (provider.unlock.customEnabled) {
            item(headlineContent = { Text(stringResource(R.string.deepseek_web_custom_on)) })
        } else {
            item(
                headlineContent = { Text(stringResource(R.string.provider_dsweb_limits_blocked)) },
                supportingContent = { Text(stringResource(R.string.provider_dsweb_limits_allowed)) },
            )
            item(
                headlineContent = { Text(stringResource(R.string.provider_dsweb_limits_readonly_note)) },
            )
        }
        item(
            headlineContent = { Text(stringResource(R.string.provider_dsweb_read_notice)) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.provider_dsweb_image_notice)) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.provider_dsweb_limits_footer)) },
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

    if (showCustomWarning) {
        DeepSeekWebCustomWarningDialog(
            required = stringResource(R.string.deepseek_web_custom_warning_body),
            onConfirm = {
                onEdit(provider.copy(unlock = provider.unlock.copy(customEnabled = true)))
                showCustomWarning = false
            },
            onDismiss = { showCustomWarning = false },
        )
    }
}

private fun CardGroupScope.deepSeekWebUnlockSwitch(
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    switchItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        leadingContent = { CardGroupIcon(icon) },
        headlineContent = { Text(stringResource(label)) },
    )
}

@Composable
private fun DeepSeekWebCustomWarningDialog(
    required: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val normalizedInput = input.filterNot(Char::isWhitespace)
    val normalizedRequired = required.filterNot(Char::isWhitespace)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deepseek_web_custom_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Text(required, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.deepseek_web_custom_warning_input)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.deepseek_web_custom_warning_progress,
                        normalizedInput.length,
                        normalizedRequired.length,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = normalizedInput == normalizedRequired,
            ) {
                Text(stringResource(R.string.deepseek_web_custom_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private val deepSeekModels = DeepSeekWebModels.defaults()
