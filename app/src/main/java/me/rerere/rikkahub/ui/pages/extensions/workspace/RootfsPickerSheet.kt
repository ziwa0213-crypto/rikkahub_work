package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.rootfs.*
import org.koin.androidx.compose.koinViewModel
import java.net.URI
import java.text.DateFormat
import java.util.Date

private enum class PickerLevel { MAIN, ADVANCED, TIERS }

@Composable
internal fun rootfsDisplayName(id: RootfsId): String = when (id.value) {
    "ubuntu-26.04" -> stringResource(R.string.rootfs_name_ubuntu)
    "debian-13" -> stringResource(R.string.rootfs_name_debian)
    "alpine-3.24" -> stringResource(R.string.rootfs_name_alpine)
    else -> id.displayName()
}

@Composable
internal fun RootfsPickerSheet(
    freeBytes: Long,
    onPick: (RootfsEntry) -> Unit,
    onCustom: (String, String?) -> Unit,
    onLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: RootfsPickerVM = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refresh() }
    val arch = remember { RootfsArch.current() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var level by rememberSaveable { mutableStateOf(PickerLevel.MAIN) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showOtherArch by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBuiltin by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var customHash by rememberSaveable { mutableStateOf("") }
    var committing by remember { mutableStateOf(false) }
    var slowLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state is CatalogState.Loading) {
        slowLoading = false
        if (state is CatalogState.Loading) { delay(2_000); slowLoading = true }
    }
    val ready = state as? CatalogState.Ready
    val entries = ready?.all ?: (state as? CatalogState.Failed)?.cached.orEmpty()
    val selected = entries.firstOrNull { it.key == selectedKey }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.8f

    fun commit(action: () -> Unit) {
        if (committing) return
        committing = true
        scope.launch { sheet.hide(); onDismiss(); action() }
    }
    fun builtin(id: RootfsId): RootfsEntry? = when {
        arch == null -> null
        state is CatalogState.Failed && entries.isEmpty() -> BuiltinRootfs.fallbackEntries(arch).firstOrNull { it.id == id }
        else -> ready?.builtin?.firstOrNull { it.id == id && it.arch == arch }
    }

    LaunchedEffect(state, pendingBuiltin) {
        val pending = pendingBuiltin ?: return@LaunchedEffect
        if (state !is CatalogState.Loading) {
            pendingBuiltin = null
            val entry = builtin(RootfsId(pending))
            if (entry != null) commit { onPick(entry) }
            else notice = context.getString(R.string.rootfs_picker_unavailable)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        BackHandler(level != PickerLevel.MAIN && !committing) {
            level = if (level == PickerLevel.TIERS) PickerLevel.ADVANCED else PickerLevel.MAIN
            selectedKey = null
            notice = null
        }
        Column(Modifier.fillMaxWidth().heightIn(max = height).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (level != PickerLevel.MAIN) {
                    TextButton(onClick = {
                        level = if (level == PickerLevel.TIERS) PickerLevel.ADVANCED else PickerLevel.MAIN
                        selectedKey = null
                        notice = null
                    }, enabled = !committing) { Text(stringResource(R.string.rootfs_picker_back)) }
                }
                Text(
                    when (level) {
                        PickerLevel.MAIN -> stringResource(R.string.rootfs_picker_title)
                        PickerLevel.ADVANCED -> stringResource(R.string.rootfs_picker_all_title)
                        PickerLevel.TIERS -> rootfsDisplayName(RootfsId(groupId.orEmpty()))
                    }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                )
                if (level == PickerLevel.ADVANCED) TextButton(onClick = vm::refresh, enabled = ready?.refreshing != true) {
                    Text(stringResource(R.string.rootfs_picker_retry))
                }
            }
            if (arch == null) PickerNotice(stringResource(R.string.rootfs_picker_arch_unsupported), error = true)
            if (state is CatalogState.Failed) {
                PickerNotice(stringResource(R.string.rootfs_picker_load_failed), error = true)
                if (level == PickerLevel.MAIN) {
                    PickerNotice(stringResource(R.string.rootfs_picker_fallback_notice))
                    TextButton(onClick = vm::refresh) { Text(stringResource(R.string.rootfs_picker_retry)) }
                }
            }
            if (ready?.offline == true) PickerNotice(stringResource(
                R.string.rootfs_picker_offline, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ready.fetchedAt))
            ))
            notice?.let { PickerNotice(it, error = true) }
            if (slowLoading || pendingBuiltin != null) LinearProgressIndicator(Modifier.fillMaxWidth())

            AnimatedContent(level, label = "rootfs-picker", modifier = Modifier.weight(1f, fill = false)) { current ->
                when (current) {
                    PickerLevel.MAIN -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text(stringResource(R.string.rootfs_picker_subtitle), style = MaterialTheme.typography.bodyMedium) }
                        item {
                            CardGroup {
                                BuiltinRootfs.ids.forEach { id ->
                                    val entry = builtin(id)
                                    val enabled = arch != null && !committing && (entry != null || state is CatalogState.Loading)
                                    item(
                                        onClick = if (enabled) ({
                                            if (entry != null) commit { onPick(entry) } else pendingBuiltin = id.value
                                        }) else null,
                                        modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                                        leadingContent = if (id == BuiltinRootfs.default) ({ Box(Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.primary)) }) else null,
                                        headlineContent = { Text(rootfsDisplayName(id)) },
                                        supportingContent = { Text(if (entry == null && state !is CatalogState.Loading) stringResource(R.string.rootfs_picker_unavailable)
                                            else "${entry?.tier?.token ?: "base"} · ${tierDescription(entry?.tier ?: RootfsTier.BASE)}") },
                                        trailingContent = { Text(entry?.sizeBytes?.takeIf { it > 0 }?.fileSizeToString() ?: "—") },
                                    )
                                }
                            }
                        }
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { level = PickerLevel.ADVANCED }) { Text(stringResource(R.string.rootfs_picker_advanced)) }
                        } }
                    }
                    PickerLevel.ADVANCED -> PullToRefreshBox(isRefreshing = ready?.refreshing == true, onRefresh = vm::refresh) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                            item { CardGroup { switchItem(
                                checked = showOtherArch, onCheckedChange = { showOtherArch = it; selectedKey = null },
                                headlineContent = { Text(stringResource(R.string.rootfs_picker_show_other_arch)) },
                            ) } }
                            if (slowLoading) items(3) { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                                Spacer(Modifier.fillMaxWidth().height(64.dp))
                            } }
                            val groups = entries.filter { showOtherArch || it.arch == arch }.groupBy { it.id.distro }.toSortedMap()
                            groups.forEach { (distro, group) ->
                                item(key = distro) { CardGroup(title = { Text(distro.replaceFirstChar { it.uppercaseChar() }) }) {
                                    group.groupBy { it.id to it.arch }.entries.sortedWith { a, b -> compareRootfsVersions(b.key.first.version, a.key.first.version) }.forEach { (key, tiers) ->
                                        val (id, rowArch) = key
                                        item(
                                            modifier = Modifier.alpha(if (rowArch == arch) 1f else 0.5f),
                                            onClick = if (!committing) ({
                                                notice = null
                                                if (rowArch != arch) notice = context.getString(R.string.rootfs_picker_arch_mismatch, rowArch.abi, arch?.abi ?: "—")
                                                else if (tiers.size == 1) selectedKey = tiers.single().key
                                                else { groupId = id.value; selectedKey = null; level = PickerLevel.TIERS }
                                            }) else null,
                                            headlineContent = { Text(rootfsDisplayName(id)) },
                                            supportingContent = { Text(if (tiers.size == 1) tiers.single().tier.token else tiers.joinToString(" / ") { it.tier.token }) },
                                            trailingContent = { Text(if (rowArch != arch) rowArch.abi else tiers.minOf { it.sizeBytes }.fileSizeToString()) },
                                        )
                                    }
                                } }
                            }
                            item { TextButton(onClick = { customExpanded = !customExpanded }, enabled = arch != null && !committing) {
                                Text(stringResource(R.string.rootfs_custom_title))
                            } }
                            if (customExpanded) item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val validUrl = runCatching {
                                        val uri = URI(customUrl.trim())
                                        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank())
                                        RootfsInstaller.ArchiveFormat.fromUrl(customUrl.trim())
                                    }.isSuccess
                                    val validHash = customHash.isBlank() || customHash.trim().matches(Regex("(?i)[a-f0-9]{64}"))
                                    OutlinedTextField(customUrl, { customUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workspace_detail_download_url)) },
                                        isError = customUrl.isNotBlank() && !validUrl, supportingText = { if (customUrl.isNotBlank() && !validUrl) Text(stringResource(R.string.rootfs_custom_bad_url)) })
                                    OutlinedTextField(customHash, { customHash = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.rootfs_custom_sha256)) },
                                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                                        isError = !validHash, supportingText = { if (!validHash) Text(stringResource(R.string.rootfs_custom_bad_sha256)) })
                                    if (customHash.isBlank()) PickerNotice(stringResource(R.string.rootfs_unverified))
                                    Button(onClick = { commit { onCustom(customUrl.trim(), customHash.trim().ifBlank { null }) } }, enabled = validUrl && validHash && !committing && arch != null) {
                                        Text(stringResource(R.string.rootfs_picker_confirm))
                                    }
                                }
                            }
                            item { TextButton(onClick = { commit(onLocal) }, enabled = arch != null && !committing) {
                                Text(stringResource(R.string.rootfs_local_title))
                            } }
                        }
                    }
                    PickerLevel.TIERS -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { CardGroup {
                            entries.filter { it.id.value == groupId && it.arch == arch }.sortedBy { it.tier.ordinal }.forEach { entry ->
                                item(onClick = { selectedKey = entry.key },
                                    leadingContent = { RadioButton(selectedKey == entry.key, onClick = { selectedKey = entry.key }) },
                                    headlineContent = { Text("${entry.tier.token} · ${entry.sizeBytes.fileSizeToString()}") },
                                    supportingContent = { Text(tierDescription(entry.tier)) },
                                    trailingContent = if (entry.tier == RootfsTier.BASE) ({ Text(stringResource(R.string.rootfs_picker_recommended), color = MaterialTheme.colorScheme.primary) }) else null,
                                )
                            }
                        } }
                    }
                }
            }
            if (selected != null && level != PickerLevel.MAIN) {
                val lowSpace = selected.sizeBytes > 0 && freeBytes / 4 < selected.sizeBytes
                Text(stringResource(R.string.rootfs_picker_disk_free, freeBytes.fileSizeToString()),
                    color = if (lowSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (lowSpace) PickerNotice(stringResource(R.string.rootfs_picker_low_space), error = true)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rootfs_picker_confirm_line, rootfsDisplayName(selected.id), selected.arch.token, selected.tier.token))
                        Button(onClick = { commit { onPick(selected) } }, enabled = !committing, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.rootfs_picker_confirm)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun tierDescription(tier: RootfsTier): String = stringResource(when (tier) {
    RootfsTier.MIN -> R.string.rootfs_picker_tier_min_desc
    RootfsTier.BASE -> R.string.rootfs_picker_tier_base_desc
    RootfsTier.DEV -> R.string.rootfs_picker_tier_dev_desc
})

@Composable
private fun PickerNotice(text: String, error: Boolean = false) {
    Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
}
