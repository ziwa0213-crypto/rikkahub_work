package me.rerere.rikkahub.ui.pages.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection.RegexInjection
import me.rerere.rikkahub.data.st.`import`.CharacterCardCodec
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun CharacterLorebookPage() {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val store: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<Lorebook?>(null) }
    var deleting by remember { mutableStateOf<Lorebook?>(null) }
    var name by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText; exportText = null
        if (uri != null && text != null) scope.launch {
            try { withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray()) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = context.getString(R.string.character_file_failed, e.localizedMessage.orEmpty()) }
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.character_lorebook_page_title)) },
            navigationIcon = { BackButton() }, scrollBehavior = scroll, colors = CustomColors.topBarColors,
            actions = { TextButton(onClick = { renaming = Lorebook(); name = "" }) { Text(stringResource(R.string.character_worldbook_create)) } })
    }, modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { CardGroup {
                characterNumber(R.string.character_worldbook_global_budget, settings.stWorldbookBudgetChars, 200..Int.MAX_VALUE, allowZero = true) { value ->
                    if (value == 0 || (value ?: 0) >= 200) scope.launch { store.update { it.copy(stWorldbookBudgetChars = value ?: 0) } }
                }
            } }
            items(settings.lorebooks, key = { it.id.toString() }) { book -> CardGroup {
                item(onClick = { nav.navigate(Screen.CharacterLorebookEntries(book.id.toString())) },
                    headlineContent = { Text(book.name) }, supportingContent = { Text(stringResource(R.string.character_worldbook_entries, book.entries.size)) },
                    trailingContent = { Row {
                        Switch(book.enabled, { enabled -> scope.launch { store.update { latest -> latest.copy(lorebooks = latest.lorebooks.map {
                            if (it.id == book.id) it.copy(enabled = enabled) else it
                        }) } } })
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            TextButton(onClick = { menu = true }) { Text("⋮") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.character_worldbook_rename)) }, onClick = { menu = false; renaming = book; name = book.name })
                                DropdownMenuItem(text = { Text(stringResource(R.string.character_export_json)) }, onClick = {
                                    menu = false; exportText = CharacterCardCodec.encodeWorldbook(book); export.launch("worldbook.json")
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; deleting = book })
                            }
                        }
                    } })
            } }
        }
    }
    renaming?.let { book -> ModalBottomSheet(onDismissRequest = { renaming = null }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.assistant_page_name)) })
            Button(enabled = name.isNotBlank(), onClick = {
                val newName = name.trim()
                scope.launch {
                    store.update { latest -> latest.copy(lorebooks = if (latest.lorebooks.any { it.id == book.id }) latest.lorebooks.map {
                        if (it.id == book.id) it.copy(name = newName) else it
                    } else latest.lorebooks + book.copy(name = newName)) }
                    renaming = null
                }
            }) { Text(stringResource(R.string.common_save)) }
        }
    } }
    RikkaConfirmDialog(show = deleting != null, title = stringResource(R.string.confirm_delete), confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel), onDismiss = { deleting = null }, onConfirm = {
            val id = deleting?.id
            scope.launch { store.update { latest -> latest.copy(lorebooks = latest.lorebooks.filterNot { it.id == id },
                characters = latest.characters.map { it.copy(lorebookIds = it.lorebookIds.filterNot { key -> key == id }.toSet()) }) } }
            deleting = null
        }) { Text(stringResource(R.string.character_worldbook_delete)) }
}

@Composable
internal fun CharacterLorebookEntriesPage(bookId: String) {
    val id = Uuid.parse(bookId)
    val settings = LocalSettings.current
    val book = settings.lorebooks.firstOrNull { it.id == id }
    val store: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Uuid?>(null) }
    fun update(entry: RegexInjection) { scope.launch { store.update { latest -> latest.copy(lorebooks = latest.lorebooks.map {
        if (it.id == id) it.copy(entries = it.entries.map { old -> if (old.id == entry.id) entry else old }) else it
    }) } } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(topBar = { LargeFlexibleTopAppBar(title = { Text(book?.name.orEmpty()) }, navigationIcon = { BackButton() },
        colors = CustomColors.topBarColors, scrollBehavior = scroll, actions = {
            TextButton(enabled = book != null, onClick = {
                val entry = RegexInjection(stPosition = 1, role = MessageRole.SYSTEM)
                scope.launch { store.update { latest -> latest.copy(lorebooks = latest.lorebooks.map {
                    if (it.id == id) it.copy(entries = it.entries + entry) else it
                }) }; editing = entry.id }
            }) { Text(stringResource(R.string.character_worldbook_new_entry)) }
        }) }, modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (book?.entries.isNullOrEmpty()) item { Text(stringResource(R.string.character_worldbook_empty)) }
            items(book?.entries.orEmpty(), key = { it.id.toString() }) { entry -> CardGroup {
                item(onClick = { editing = entry.id }, headlineContent = { Text(entry.name.ifBlank { entry.keywords.firstOrNull().orEmpty() }) },
                    supportingContent = { Text(stringResource(characterPositionLabels[entry.stPosition.coerceIn(0, 7)]) + " · " + entry.keywords.size +
                        if (entry.constantActive) " · " + stringResource(R.string.character_entry_constant) else "") },
                    trailingContent = { Switch(entry.enabled, { update(entry.copy(enabled = it)) }) })
            } }
        }
    }
    book?.entries?.firstOrNull { it.id == editing }?.let { entry -> WorldbookEntrySheet(entry, ::update, { editing = null }, {
        scope.launch { store.update { latest -> latest.copy(lorebooks = latest.lorebooks.map {
            if (it.id == id) it.copy(entries = it.entries.filterNot { e -> e.id == entry.id }) else it
        }) } }; editing = null
    }) }
}
