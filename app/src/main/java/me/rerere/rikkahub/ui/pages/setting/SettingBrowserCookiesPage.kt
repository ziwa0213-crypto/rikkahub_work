package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cookie
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Internet
import me.rerere.rikkahub.R
import me.rerere.rikkahub.browser.BrowserDataCleaner
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingBrowserCookiesPage(settingsStore: SettingsStore = koinInject()) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val hosts = settings.browserVisitedHosts
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val operationFailed = stringResource(R.string.error_title_operation)
    val clearedMessage = stringResource(R.string.browser_cookies_cleared)
    val allClearedMessage = stringResource(R.string.browser_clear_data_done)

    var manualDomain by remember { mutableStateOf("") }
    var manualDomainInvalid by remember { mutableStateOf(false) }
    var cookiePresence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var refreshVersion by remember { mutableStateOf(0) }
    var selectedSite by remember { mutableStateOf<String?>(null) }
    var selectedCookieNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var namesLoading by remember { mutableStateOf(false) }
    var pendingSiteClear by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(hosts, refreshVersion) {
        cookiePresence = withContext(Dispatchers.IO) {
            hosts.associateWith { host -> BrowserDataCleaner.hasCookies(host) }
        }
    }
    LaunchedEffect(selectedSite, refreshVersion) {
        val host = selectedSite ?: return@LaunchedEffect
        namesLoading = true
        selectedCookieNames = withContext(Dispatchers.IO) { BrowserDataCleaner.cookieNames(host) }
        namesLoading = false
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.browser_cookies_title)) },
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
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.browser_cookies_desc)) },
                        supportingContent = { Text(stringResource(R.string.browser_cookies_shared_notice)) },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.browser_cookies_manual_hint)) },
                ) {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Cookie) },
                        headlineContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = manualDomain,
                                    onValueChange = {
                                        manualDomain = it
                                        manualDomainInvalid = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    isError = manualDomainInvalid,
                                    placeholder = { Text("example.com") },
                                    supportingText = if (manualDomainInvalid) {
                                        { Text(stringResource(R.string.browser_cookies_invalid_domain)) }
                                    } else null,
                                )
                                Button(
                                    onClick = {
                                        val host = BrowserDataCleaner.normalizeInputDomain(manualDomain)
                                        if (host == null) {
                                            manualDomainInvalid = true
                                        } else {
                                            pendingSiteClear = host
                                            manualDomainInvalid = false
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.browser_cookies_clear_button))
                                }
                            }
                        },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.browser_cookies_sites_title)) },
                ) {
                    if (hosts.isEmpty()) {
                        item(headlineContent = { Text(stringResource(R.string.browser_cookies_empty)) })
                    } else {
                        hosts.forEach { host ->
                            item(
                                onClick = { selectedSite = host },
                                leadingContent = { CardGroupIcon(HugeIcons.Internet) },
                                headlineContent = { Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                Text(stringResource(R.string.browser_cookies_site_detail_hint))
                                },
                                trailingContent = {
                                    Text(
                                        stringResource(
                                            when (cookiePresence[host]) {
                                                true -> R.string.browser_cookies_present
                                                false -> R.string.browser_cookies_absent
                                                null -> R.string.browser_cookies_checking
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        onClick = { showClearAllConfirmation = true },
                        leadingContent = { CardGroupIcon(HugeIcons.Delete01) },
                        headlineContent = { Text(stringResource(R.string.browser_cookies_remove_all)) },
                        supportingContent = { Text(stringResource(R.string.browser_clear_data_desc)) },
                    )
                }
            }
        }
    }

    selectedSite?.let { host ->
        ModalBottomSheet(
            onDismissRequest = { selectedSite = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.browser_cookies_names_title, selectedCookieNames.size))
                    when {
                        namesLoading -> Text(stringResource(R.string.browser_cookies_checking))
                        selectedCookieNames.isEmpty() -> Text(stringResource(R.string.browser_cookies_absent))
                        else -> selectedCookieNames.forEach { name ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.browser_cookies_value_hidden),
                                    modifier = Modifier.padding(start = 12.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.browser_cookies_clear_site_warning))
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingSiteClear = host },
                ) {
                    Text(stringResource(R.string.browser_cookies_clear_site))
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedSite = null },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingSiteClear != null,
        title = stringResource(R.string.browser_cookies_clear_site),
        confirmText = stringResource(R.string.browser_cookies_clear_button),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val host = pendingSiteClear ?: return@RikkaConfirmDialog
            pendingSiteClear = null
            selectedSite = null
            scope.launch {
                runCatching { BrowserDataCleaner.clearCookiesForHost(host) }
                    .onSuccess {
                        refreshVersion++
                        toaster.show(clearedMessage)
                    }
                    .onFailure { toaster.show(operationFailed, type = ToastType.Error) }
            }
        },
        onDismiss = { pendingSiteClear = null },
        text = {
            Text(
                stringResource(R.string.browser_cookies_clear_site_confirm, pendingSiteClear.orEmpty()) +
                    "\n\n" + stringResource(R.string.browser_cookies_clear_site_warning),
            )
        },
    )

    RikkaConfirmDialog(
        show = showClearAllConfirmation,
        title = stringResource(R.string.browser_cookies_remove_all),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showClearAllConfirmation = false
            scope.launch {
                runCatching { BrowserDataCleaner.clear(context) }
                    .onSuccess {
                        refreshVersion++
                        toaster.show(allClearedMessage)
                    }
                    .onFailure { toaster.show(operationFailed, type = ToastType.Error) }
            }
        },
        onDismiss = { showClearAllConfirmation = false },
        text = { Text(stringResource(R.string.browser_cookies_clear_all_confirm)) },
    )
}
