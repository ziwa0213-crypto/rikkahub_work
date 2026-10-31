package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Cookie
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Cursor01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Filter
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.ListView
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.BrowserDataCleaner
import me.rerere.rikkahub.browser.BrowserToolCategory
import me.rerere.rikkahub.browser.BrowserToolRegistry
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun SettingBrowserPage(
    settingsStore: SettingsStore = koinInject(),
    controller: BrowserController = koinInject(),
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val filterEnabled by settingsStore.browserFilterEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val busyMessage = stringResource(R.string.browser_ui_busy)
    val clearDone = stringResource(R.string.browser_clear_data_done)
    val operationFailed = stringResource(R.string.error_title_operation)
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showFilterWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.browser_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.browser_group_actions)) },
                ) {
                    item(
                        onClick = {
                            if (controller.active != null) {
                                toaster.show(busyMessage, type = ToastType.Error)
                            } else {
                                navController.navigate(Screen.Browser(Uuid.random().toString(), "", standalone = true))
                            }
                        },
                        leadingContent = { Icon(HugeIcons.Internet, null) },
                        headlineContent = { Text(stringResource(R.string.browser_open_action)) },
                        supportingContent = { Text(stringResource(R.string.browser_open_action_desc)) },
                    )
                    item(
                        onClick = { showClearConfirmation = true },
                        leadingContent = { CardGroupIcon(HugeIcons.Delete01) },
                        headlineContent = { Text(stringResource(R.string.browser_clear_data)) },
                        supportingContent = { Text(stringResource(R.string.browser_clear_data_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingBrowserCookies) },
                        leadingContent = { CardGroupIcon(HugeIcons.Cookie) },
                        headlineContent = { Text(stringResource(R.string.browser_cookies_title)) },
                        supportingContent = { Text(stringResource(R.string.browser_cookies_desc)) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.browser_group_tools)) },
                ) {
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.browser_tools_hint), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        },
                    )
                    BrowserToolCategory.entries.forEach { category ->
                        item(
                            headlineContent = {
                                Text(
                                    stringResource(category.titleResource()),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        BrowserToolRegistry.forCategory(category).forEach { tool ->
                            val enabled = BrowserToolRegistry.isEnabled(settings.browserToolsDisabled, tool.name)
                            switchItem(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    val disabled = settings.browserToolsDisabled.toMutableSet()
                                    if (checked) disabled.remove(tool.name) else disabled.add(tool.name)
                                    scope.launch { settingsStore.setBrowserToolsDisabled(disabled.toList()) }
                                },
                                leadingContent = {
                                    CardGroupIcon(
                                        when (tool.name) {
                                            "browser_open" -> HugeIcons.Internet
                                            "browser_state" -> HugeIcons.View
                                            "browser_read" -> HugeIcons.FileView
                                            "browser_list_actions" -> HugeIcons.ListView
                                            "browser_click" -> HugeIcons.Cursor01
                                            "browser_type" -> HugeIcons.PencilEdit01
                                            "browser_scroll" -> HugeIcons.ArrowDown01
                                            else -> HugeIcons.Tick01
                                        },
                                    )
                                },
                                headlineContent = { Text(stringResource(tool.titleRes)) },
                                supportingContent = { Text(stringResource(tool.descriptionRes)) },
                            )
                        }
                    }
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.browser_group_defaults)) },
                ) {
                    switchItem(
                        checked = filterEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) scope.launch { settingsStore.setBrowserFilterEnabled(true) }
                            else showFilterWarning = true
                        },
                        leadingContent = { CardGroupIcon(HugeIcons.Filter) },
                        headlineContent = { Text(stringResource(R.string.browser_setting_filter_title)) },
                        supportingContent = { Text(stringResource(R.string.browser_setting_filter_desc)) },
                    )
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.SmartPhone01) },
                        headlineContent = { Text(stringResource(if (settings.browserDesktopMode) R.string.browser_mode_desktop else R.string.browser_mode_mobile)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.browser_mode_desktop_note))
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    listOf(false, true).forEachIndexed { index, desktop ->
                                        SegmentedButton(
                                            selected = settings.browserDesktopMode == desktop,
                                            onClick = { scope.launch { settingsStore.setBrowserDesktopMode(desktop) } },
                                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        ) {
                                            Text(stringResource(if (desktop) R.string.browser_mode_desktop else R.string.browser_mode_mobile))
                                        }
                                    }
                                }
                            }
                        },
                    )
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
                        headlineContent = { Text(stringResource(R.string.browser_tool_timeout_title)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.browser_tool_timeout_desc))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedNumberInput(
                                        value = (settings.browserToolTimeoutMs / 1_000L).toInt(),
                                        onValueChange = { seconds ->
                                            scope.launch {
                                                settingsStore.setBrowserToolTimeoutMs(seconds.coerceIn(10, 600) * 1_000L)
                                            }
                                        },
                                        modifier = Modifier.width(112.dp),
                                    )
                                    Text(stringResource(R.string.browser_unit_seconds))
                                }
                            }
                        },
                    )
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
                        headlineContent = { Text(stringResource(R.string.browser_task_timeout_title)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.browser_task_timeout_desc))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedNumberInput(
                                        value = (settings.browserTaskTimeoutMs / 60_000L).toInt(),
                                        onValueChange = { minutes ->
                                            scope.launch {
                                                settingsStore.setBrowserTaskTimeoutMs(minutes.coerceIn(1, 60) * 60_000L)
                                            }
                                        },
                                        modifier = Modifier.width(112.dp),
                                    )
                                    Text(stringResource(R.string.browser_unit_minutes))
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.browser_setting_notes_title)) },
                        supportingContent = {
                            Text(
                                listOf(
                                    R.string.browser_setting_notes_security,
                                    R.string.browser_setting_notes_limits,
                                    R.string.browser_ui_risk_notice,
                                    R.string.browser_ui_reload_notice,
                                ).joinToString("\n") { context.getString(it) },
                            )
                        },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = showClearConfirmation,
        title = stringResource(R.string.browser_clear_data),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showClearConfirmation = false
            scope.launch {
                runCatching { BrowserDataCleaner.clear(context) }
                    .onSuccess { toaster.show(clearDone) }
                    .onFailure { toaster.show(operationFailed, type = ToastType.Error) }
            }
        },
        onDismiss = { showClearConfirmation = false },
        text = { Text(stringResource(R.string.browser_cookies_clear_all_confirm)) },
    )

    RikkaConfirmDialog(
        show = showFilterWarning,
        title = stringResource(R.string.browser_setting_filter_title),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showFilterWarning = false
            scope.launch { settingsStore.setBrowserFilterEnabled(false) }
        },
        onDismiss = { showFilterWarning = false },
        text = { Text(stringResource(R.string.browser_setting_filter_off_warning)) },
    )
}

@Composable
private fun BrowserToolCategory.titleResource(): Int = when (this) {
    BrowserToolCategory.READ -> R.string.browser_category_read
    BrowserToolCategory.WRITE -> R.string.browser_category_write
    BrowserToolCategory.LOOP -> R.string.browser_category_loop
}
