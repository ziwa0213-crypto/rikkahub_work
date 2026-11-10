package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.PULSE_MEMORY_ID
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.MemoryConsolidationSection
import org.koin.compose.koinInject

/** Pulse has its own memory bucket and is deliberately absent from the assistant library. */
@Composable
fun PulseMemoryPage() {
    val nav = LocalNavController.current
    val repository: MemoryRepository = koinInject()
    val store: SettingsStore = koinInject()
    val scheduler: MemoryConsolidationScheduler = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val status by remember(scheduler) { scheduler.statusOf(PULSE_MEMORY_ID) }
        .collectAsStateWithLifecycle(initialValue = MemoryConsolidationStatus())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val files by remember(repository) { repository.getFilesFlow(PULSE_MEMORY_ID) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pulse_memory_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { nav.navigate(Screen.MemoryFile(PULSE_MEMORY_ID)) }) {
                            Icon(HugeIcons.Add01, stringResource(R.string.pulse_memory_new))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                listOf(R.string.pulse_memory_tab_files, R.string.pulse_memory_tab_dream).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (tab == 1) 12.dp else 2.dp),
            ) {
                if (tab == 1) {
                    item {
                        MemoryConsolidationSection(status = status, onOrganize = { scheduler.organize(PULSE_MEMORY_ID) })
                    }
                    item {
                        CardGroup {
                            switchItem(
                                checked = settings.pulse.autoConsolidateMemory,
                                enabled = !settings.init && settings.pulse.enableMemory,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        store.update { latest ->
                                            latest.copy(pulse = latest.pulse.copy(autoConsolidateMemory = enabled))
                                        }
                                    }
                                },
                                headlineContent = { Text(stringResource(R.string.pulse_memory_auto_organize)) },
                                supportingContent = { Text(stringResource(R.string.pulse_memory_auto_organize_desc)) },
                            )
                        }
                    }
                } else {
                    if (files.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.pulse_memory_empty),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(files, key = { it.path }) { file ->
                        ListItem(
                            headlineContent = { Text(file.title.ifBlank { file.path }) },
                            supportingContent = file.description.takeIf { it.isNotBlank() }?.let { description ->
                                { Text(description) }
                            },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            modifier = Modifier.clickable { nav.navigate(Screen.MemoryFile(PULSE_MEMORY_ID, file.path)) },
                        )
                    }
                }
            }
        }
    }
}
