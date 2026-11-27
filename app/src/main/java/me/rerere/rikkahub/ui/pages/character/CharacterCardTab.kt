package me.rerere.rikkahub.ui.pages.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.st.`import`.CharacterCardCodec
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.ui.components.RikkaConfirmDialog
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CharacterCardTab(character: Character, update: ((Character) -> Character) -> Unit,
    installEnabled: Boolean, installing: Boolean, onInstall: () -> Unit, onDelete: () -> Unit) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    var metadata by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteGreeting by remember { mutableStateOf<Int?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = exportBytes
        exportBytes = null
        if (uri != null && bytes != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) } }
                message = context.getString(R.string.character_export_complete)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = context.getString(R.string.character_file_failed, e.localizedMessage.orEmpty()) }
        }
    }
    var originalPng by remember(character.avatar) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(character.avatar) {
        originalPng = withContext(Dispatchers.IO) {
            (character.avatar as? Avatar.Image)?.let { avatar -> runCatching {
                context.contentResolver.openInputStream(Uri.parse(avatar.url))?.use { it.readBytes() }
                    ?.takeIf { it.size >= 8 && it[0] == 137.toByte() && it[1] == 80.toByte() && it[2] == 78.toByte() && it[3] == 71.toByte() }
            }.getOrNull() }
        }
    }
    fun prepareExport(png: Boolean) {
        scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val books = settings.lorebooks.filter { it.id in character.lorebookIds }
                    if (png) CharacterCardCodec.encodePng(originalPng, character, books)
                    else CharacterCardCodec.encodeJson(character, books).toByteArray()
                }
                exportBytes = bytes
                exporting = false
                val safeName = character.name.replace(Regex("[\\/\\\\:*?\"<>|]"), "_").ifBlank { "character" }
                export.launch("$safeName.${if (png) "png" else "json"}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = context.getString(R.string.character_file_failed, e.localizedMessage.orEmpty()) }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message?.let { item { Text(it, Modifier.padding(horizontal = 16.dp)) } }
        item { CardGroup(Modifier.padding(horizontal = 8.dp), title = { Text(stringResource(R.string.character_section_basic)) }) {
            item(headlineContent = { Text(stringResource(R.string.character_card)) }, leadingContent = {
                UIAvatar(name = character.name, value = character.avatar, modifier = Modifier.size(48.dp),
                    onUpdate = { avatar -> update { it.copy(avatar = avatar) } })
            })
            characterTextField(R.string.assistant_page_name, character.name) { v -> update { it.copy(name = v) } }
            characterTextField(R.string.character_nickname, character.nickname) { v -> update { it.copy(nickname = v) } }
            characterTextField(R.string.character_description, character.description, 3) { v -> update { it.copy(description = v) } }
            characterTextField(R.string.character_personality, character.personality, 3) { v -> update { it.copy(personality = v) } }
            characterTextField(R.string.character_scenario, character.scenario, 3) { v -> update { it.copy(scenario = v) } }
        } }
        item { CardGroup(Modifier.padding(horizontal = 8.dp), title = { Text(stringResource(R.string.character_section_dialogue)) }) {
            characterTextField(R.string.character_greeting, character.firstMessage, 3) { v -> update { it.copy(firstMessage = v) } }
            formItem(headlineContent = { Text(stringResource(R.string.character_alternate_greetings)) }) {
                if (character.alternateGreetings.isEmpty()) Text(stringResource(R.string.character_greetings_empty))
                character.alternateGreetings.forEachIndexed { index, greeting ->
                    OutlinedTextField(greeting, { value -> update { current -> current.copy(alternateGreetings =
                        current.alternateGreetings.mapIndexed { i, old -> if (i == index) value else old }) } },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
                    TextButton(onClick = { deleteGreeting = index }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = { update { it.copy(alternateGreetings = it.alternateGreetings + "") } }) { Text(stringResource(R.string.add)) }
            }
            characterTextField(R.string.character_example, character.exampleMessages, 3) { v -> update { it.copy(exampleMessages = v) } }
            characterTextField(R.string.character_preset_messages, character.presetMessages.firstOrNull()?.parts?.filterIsInstance<UIMessagePart.Text>()?.firstOrNull()?.text.orEmpty(), 3) { v ->
                update { current -> current.copy(presetMessages = current.presetMessages.editFirstText(v)) }
            }
            characterTextField(R.string.character_depth_prompt, character.depthPrompt, 3) { v -> update { it.copy(depthPrompt = v) } }
            characterNumber(R.string.character_depth, character.depthPromptDepth, 1..20) { v -> update { it.copy(depthPromptDepth = v ?: 4) } }
            characterChoice(R.string.character_message_role, characterRoleValues.indexOf(character.depthPromptRole), characterRoleLabels) { index ->
                update { it.copy(depthPromptRole = characterRoleValues[index]) }
            }
            characterTextField(R.string.character_phi, character.postHistoryInstructions, 3) { v -> update { it.copy(postHistoryInstructions = v) } }
        } }
        item { CardGroup(Modifier.padding(horizontal = 8.dp), title = { Text(stringResource(R.string.character_section_metadata)) }) {
            switchItem(checked = metadata, onCheckedChange = { metadata = it }, headlineContent = { Text(stringResource(R.string.character_section_metadata)) })
            if (metadata) {
                item(headlineContent = { Text(stringResource(R.string.character_creator_notes)) }, supportingContent = { Text(character.creatorNotes) })
                characterTextField(R.string.character_version, character.characterVersion) { v -> update { it.copy(characterVersion = v) } }
                formItem { CharacterChips(R.string.character_tags, character.tags) { tags -> update { it.copy(tags = tags) } } }
                item(headlineContent = { Text(stringResource(R.string.character_dates,
                    DateFormat.getDateTimeInstance().format(Date(character.createdAt)), DateFormat.getDateTimeInstance().format(Date(character.updatedAt)))) })
                if (character.rawExtensions.isNotBlank()) item(headlineContent = { Text("extensions") }, supportingContent = { Text(character.rawExtensions) })
                if (character.rawCardTruncated || character.rawCardJson.isBlank()) item(headlineContent = { Text(stringResource(
                    if (character.rawCardTruncated) R.string.character_raw_truncated else R.string.character_raw_absent)) })
            }
        } }
        item { CardGroup(Modifier.padding(horizontal = 8.dp), title = { Text(stringResource(R.string.character_section_maintenance)) }) {
            formItem { StylePackSection(character, update) }
            item(onClick = { if (installEnabled && !installing) onInstall() },
                headlineContent = { Text(stringResource(R.string.character_workspace_dependencies)) },
                supportingContent = { if (!installEnabled) Text(stringResource(R.string.pulse_workspace_setup_message)) })
            characterNumber(R.string.character_worldbook_budget, character.stWorldbookBudgetChars, 200..Int.MAX_VALUE, nullable = true) { v ->
                update { it.copy(stWorldbookBudgetChars = v) }
            }
            item(onClick = { nav.navigate(Screen.CharacterRegexScripts(character.id.toString())) }, headlineContent = { Text(stringResource(R.string.character_regex_title)) })
            item(onClick = { exporting = true }, headlineContent = { Text(stringResource(R.string.character_export_card)) })
            item(onClick = onDelete, headlineContent = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) })
        } }
    }
    RikkaConfirmDialog(show = deleteGreeting != null, title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete), dismissText = stringResource(R.string.cancel),
        onDismiss = { deleteGreeting = null }, onConfirm = {
            val index = deleteGreeting
            update { it.copy(alternateGreetings = it.alternateGreetings.filterIndexed { i, _ -> i != index }) }
            deleteGreeting = null
        }) { Text(character.alternateGreetings.getOrNull(deleteGreeting ?: -1).orEmpty()) }
    if (exporting) ModalBottomSheet(onDismissRequest = { if (!busy) exporting = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.character_export_card), style = MaterialTheme.typography.titleLarge)
            if (character.rawCardTruncated || character.rawCardJson.isBlank()) Text(stringResource(
                if (character.rawCardTruncated) R.string.character_raw_truncated else R.string.character_raw_absent))
            Button(enabled = !busy, onClick = { prepareExport(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.character_export_json)) }
            OutlinedButton(enabled = !busy && originalPng != null, onClick = { prepareExport(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.character_export_png)) }
            if (originalPng == null) Text(stringResource(R.string.character_png_unavailable))
        }
    }
}

/** Edit just the first text part; the first message role/attachments and all remaining messages survive. */
internal fun List<UIMessage>.editFirstText(text: String): List<UIMessage> {
    val first = firstOrNull() ?: return if (text.isBlank()) emptyList() else listOf(UIMessage.assistant(text))
    val index = first.parts.indexOfFirst { it is UIMessagePart.Text }
    val parts = if (index < 0) first.parts + UIMessagePart.Text(text) else first.parts.mapIndexed { i, part ->
        if (i == index) (part as UIMessagePart.Text).copy(text = text) else part
    }
    return listOf(first.copy(parts = parts)) + drop(1)
}
