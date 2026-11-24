package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.StylePack
import me.rerere.rikkahub.data.model.MAX_STYLE_PACK_RULES
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject

@Composable
internal fun StylePackSection(character: Character, update: ((Character) -> Character) -> Unit) {
    val settings = LocalSettings.current
    val store: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val pack = settings.stylePacks.firstOrNull { it.id == character.stylePackId }
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Text(stringResource(R.string.character_style), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = character.styleNotesEnabled, onCheckedChange = { enabled -> update { it.copy(styleNotesEnabled = enabled) } })
        Box {
            TextButton(onClick = { choosing = true }) { Text(pack?.name ?: stringResource(R.string.character_style_none)) }
            DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.character_style_none)) }, onClick = {
                    choosing = false; update { it.copy(stylePackId = null) }
                })
                settings.stylePacks.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = {
                    choosing = false; update { it.copy(stylePackId = item.id) }
                }) }
            }
        }
        TextButton(onClick = {
            name = pack?.name.orEmpty()
            rules = pack?.rules.orEmpty().joinToString("\n")
            editing = true
        }) { Text(stringResource(R.string.edit)) }
    }
    if (editing) {
        val lines = rules.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.character_style)) },
            text = { Column {
                CharacterField(R.string.assistant_page_name, name) { name = it }
                OutlinedTextField(rules, { rules = it }, label = { Text(stringResource(R.string.character_style_rules)) },
                    isError = lines.size > MAX_STYLE_PACK_RULES, maxLines = 8, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank() && lines.size in 1..MAX_STYLE_PACK_RULES, onClick = {
                val draft = (pack?.takeUnless { it.builtIn } ?: StylePack()).copy(name = name.trim(), rules = lines)
                scope.launch { store.update { latest -> latest.copy(
                    stylePacks = latest.stylePacks.filterNot { it.id == draft.id } + draft,
                    characters = latest.characters.map { if (it.id == character.id) it.copy(stylePackId = draft.id, styleNotesEnabled = true) else it },
                ) } }
                editing = false
            }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
