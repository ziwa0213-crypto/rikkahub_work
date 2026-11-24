package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.defaultCharacterPromptModules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.group.ModeSwitcher
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.pages.group.selectedGroup
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun CharacterPage() {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val store: SettingsStore = koinInject()
    val repo: ConversationRepository = koinInject()
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.character_list_title)) }, navigationIcon = { BackButton() }, actions = {
            TextButton(onClick = { nav.navigate(Screen.CharacterLorebooks) }) {
                Text(stringResource(R.string.character_lorebook_page_title))
            }
        })
    }) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeSwitcher(
                        mode = settings.uiMode,
                        experimentalCharacterMode = settings.experimentalCharacterMode,
                        onSelect = { mode ->
                            scope.launch {
                                store.update { it.copy(uiMode = mode) }
                                when (mode) {
                                    UiMode.CHAT -> navigateToChatPage(nav)
                                    UiMode.ROLE -> Unit
                                    UiMode.GROUP -> {
                                        val id = settings.selectedGroup(null)?.conversationIds?.lastOrNull()
                                        if (id == null) nav.clearAndNavigate(Screen.Groups) else navigateToChatPage(nav, id)
                                    }
                                    UiMode.PULSE -> nav.clearAndNavigate(Screen.Pulse)
                                }
                            }
                        },
                    )
                    TextButton(onClick = { nav.navigate(Screen.CharacterTransfer) }) { Text(stringResource(R.string.character_transfer)) }
                    TextButton(onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            var newId = Uuid.random()
                            store.update { current ->
                                val ids = current.characters.map { it.id }.toSet() + current.assistants.map { it.id } + current.deletedCharacterIds
                                newId = generateSequence { Uuid.random() }.first { it !in ids }
                                current.copy(characters = current.characters + Character(
                                    id = newId,
                                    promptModules = defaultCharacterPromptModules(),
                                    createdAt = now,
                                    updatedAt = now,
                                ))
                            }
                            nav.navigate(Screen.CharacterDetail(newId.toString()))
                        }
                    }) { Text(stringResource(R.string.character_create)) }
                }
            }
            if (settings.characters.isEmpty()) item {
                Text(stringResource(R.string.character_no_selection), Modifier.padding(16.dp))
            }
            items(settings.characters, key = { it.id.toString() }) { character ->
                ListItem(
                    leadingContent = { UIAvatar(value = character.avatar, name = character.name, modifier = Modifier.size(40.dp)) },
                    supportingContent = { if (character.description.isNotBlank()) Text(character.description, maxLines = 2) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { nav.navigate(Screen.CharacterDetail(character.id.toString())) }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = {
                                scope.launch {
                                    store.update { it.copy(uiMode = UiMode.ROLE, characterId = character.id) }
                                    navigateToChatPage(nav)
                                }
                            }) { Text(stringResource(R.string.chat_page_new_chat)) }
                        }
                    },
                ) {
                    Text(character.name.ifBlank { stringResource(R.string.group_mode_role) })
                }
            }
            if (settings.deletedCharacterIds.isNotEmpty()) item {
                val history by produceState(emptyList<me.rerere.rikkahub.data.model.Conversation>(), settings.deletedCharacterIds) {
                    value = settings.deletedCharacterIds.flatMap { id ->
                        repo.getConversationsOfAssistant(id).first()
                    }
                }
                history.forEach { chat ->
                    TextButton(onClick = { navigateToChatPage(nav, chat.id) }) {
                        Text(chat.title.ifBlank { stringResource(R.string.chat_page_new_chat) } + " " + stringResource(R.string.character_deleted_suffix))
                    }
                }
            }
        }
    }
}
