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
import kotlinx.serialization.json.*
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.parseRegexScript
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun CharacterRegexScriptsPage(characterId: String?) {
    val settings = LocalSettings.current
    val store: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val id = characterId?.let(Uuid::parse)
    val character = settings.characters.firstOrNull { it.id == id }
    val scripts = if (id == null) settings.stRegexScripts else character?.stRegexScripts.orEmpty()
    var editing by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(change: (List<RegexScript>) -> List<RegexScript>) { scope.launch {
        store.update { latest -> if (id == null) latest.copy(stRegexScripts = change(latest.stRegexScripts)) else latest.copy(characters = latest.characters.map {
            if (it.id == id) it.copy(stRegexScripts = change(it.stRegexScripts), updatedAt = System.currentTimeMillis()) else it
        }) }
    } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val text = checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    parseRegexScriptsJson(text).map { it.copy(id = Uuid.random().toString()) }
                }
                if (imported.isEmpty()) error = context.getString(R.string.character_regex_invalid) else update { it + imported }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = context.getString(R.string.character_regex_invalid) }
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(if (id == null) R.string.character_regex_global else R.string.character_regex_title)) },
        navigationIcon = { BackButton() }, colors = CustomColors.topBarColors, scrollBehavior = scroll, actions = {
            TextButton(enabled = id == null || character != null, onClick = {
                val script = RegexScript(id = Uuid.random().toString(), scriptName = context.getString(R.string.character_regex_title),
                    placement = listOf(RegexPlacement.AI_OUTPUT), disabled = true)
                update { it + script }; editing = script.id
            }) { Text(stringResource(R.string.add)) }
        }) }, modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CardGroup {
                item(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, headlineContent = { Text(stringResource(R.string.character_regex_import)) })
                if (scripts.isNotEmpty()) item(onClick = { clear = true }, headlineContent = { Text(stringResource(R.string.character_regex_clear), color = MaterialTheme.colorScheme.error) })
            } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(scripts, key = { it.id }) { script -> CardGroup {
                item(onClick = { editing = script.id }, headlineContent = { Text(script.scriptName) }, supportingContent = {
                    Text((if (RegexPlacement.USER_INPUT in script.placement) stringResource(R.string.character_regex_input) + " · " else "") +
                        (if (RegexPlacement.AI_OUTPUT in script.placement) stringResource(R.string.character_regex_output) + " · " else "") +
                        stringResource(if (script.disabled) R.string.character_regex_disabled else R.string.character_regex_enabled))
                }, trailingContent = { Switch(!script.disabled, { enabled -> update { list -> list.map { if (it.id == script.id) it.copy(disabled = !enabled) else it } } }) })
            } }
        }
    }
    scripts.firstOrNull { it.id == editing }?.let { script -> RegexScriptSheet(script, { changed ->
        update { list -> list.map { if (it.id == changed.id) changed else it } }
    }, { editing = null }, { update { list -> list.filterNot { it.id == script.id } }; editing = null }) }
    RikkaConfirmDialog(show = clear, title = stringResource(R.string.confirm_delete), confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel), onDismiss = { clear = false }, onConfirm = { update { emptyList() }; clear = false }) {
        Text(stringResource(R.string.character_regex_clear_confirm))
    }
}

internal fun parseRegexScriptsJson(text: String): List<RegexScript> {
    val root = Json.parseToJsonElement(text)
    val rows = when (root) {
        is JsonArray -> root.toList()
        is JsonObject -> (root["regex_scripts"] as? JsonArray)?.toList() ?: (root["scripts"] as? JsonArray)?.toList() ?: listOf(root)
        else -> emptyList()
    }
    return rows.mapNotNull(::parseRegexScript).filter { it.findRegex.isNotBlank() }
}

@Composable
private fun RegexScriptSheet(script: RegexScript, update: (RegexScript) -> Unit, dismiss: () -> Unit, delete: () -> Unit) {
    var draft by remember(script.id) { mutableStateOf(script) }
    var deleting by remember { mutableStateOf(false) }
    fun change(block: (RegexScript) -> RegexScript) { draft = block(draft); update(draft) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CardGroup {
                characterTextField(R.string.assistant_page_name, draft.scriptName) { v -> change { it.copy(scriptName = v) } }
                characterTextField(R.string.character_regex_find, draft.findRegex, 3) { v -> change { it.copy(findRegex = v) } }
                characterTextField(R.string.character_regex_replace, draft.replaceString, 3) { v -> change { it.copy(replaceString = v) } }
                // Placement uses the reference 1/2 constants. Display/prompt are flags, not invented placement values.
                toggle(R.string.character_regex_input, RegexPlacement.USER_INPUT in draft.placement) { v -> change {
                    it.copy(placement = if (v) (it.placement + RegexPlacement.USER_INPUT).distinct() else it.placement - RegexPlacement.USER_INPUT)
                } }
                toggle(R.string.character_regex_output, RegexPlacement.AI_OUTPUT in draft.placement) { v -> change {
                    it.copy(placement = if (v) (it.placement + RegexPlacement.AI_OUTPUT).distinct() else it.placement - RegexPlacement.AI_OUTPUT)
                } }
                toggle(R.string.character_regex_markdown, draft.markdownOnly) { v -> change { it.copy(markdownOnly = v) } }
                toggle(R.string.character_regex_prompt, draft.promptOnly) { v -> change { it.copy(promptOnly = v) } }
                toggle(R.string.character_regex_edit, draft.runOnEdit) { v -> change { it.copy(runOnEdit = v) } }
                characterNumber(R.string.character_regex_min_depth, draft.minDepth, -1..Int.MAX_VALUE, true) { v -> change { it.copy(minDepth = v) } }
                characterNumber(R.string.character_regex_max_depth, draft.maxDepth, 0..Int.MAX_VALUE, true) { v -> change { it.copy(maxDepth = v) } }
                characterChoice(R.string.character_regex_substitute, draft.substituteRegex, listOf(R.string.character_regex_substitute_none,
                    R.string.character_regex_substitute_raw, R.string.character_regex_substitute_escaped)) { v -> change { it.copy(substituteRegex = v) } }
                characterTextField(R.string.character_regex_trim, draft.trimStrings.joinToString("\n"), 3) { v -> change { it.copy(trimStrings = v.lines().filter(String::isNotEmpty)) } }
                toggle(R.string.character_regex_enabled, !draft.disabled) { v -> change { it.copy(disabled = !v) } }
                item(onClick = { deleting = true }, headlineContent = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) })
            } }
        }
    }
    RikkaConfirmDialog(show = deleting, title = stringResource(R.string.confirm_delete), confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel), onDismiss = { deleting = false }, onConfirm = delete) { Text(stringResource(R.string.character_regex_delete)) }
}
