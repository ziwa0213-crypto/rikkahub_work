package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import kotlin.uuid.Uuid

fun Settings.findCharacter(id: Uuid?): Character? = characters.firstOrNull { it.id == id }

fun Conversation.isDeletedCharacterChat(settings: Settings): Boolean =
    !isGroupChat() && !isPulseChat() && assistantId in settings.deletedCharacterIds

fun Conversation.isCharacterChat(settings: Settings): Boolean =
    !isGroupChat() && !isPulseChat() &&
        (settings.findCharacter(assistantId) != null || isDeletedCharacterChat(settings))

/** Worldbooks belong to work while role mode is hidden, then move to role when enabled. */
internal fun Conversation.canUseLorebooks(settings: Settings): Boolean = when {
    isGroupChat() || isPulseChat() -> false
    isCharacterChat(settings) -> settings.experimentalCharacterMode
    else -> !settings.experimentalCharacterMode
}

internal fun Settings.selectedCharacter(): Character? = findCharacter(characterId) ?: characters.firstOrNull()

internal fun Settings.newConversationOwnerId(): Uuid? = when (uiMode) {
    UiMode.ROLE -> selectedCharacter()?.id
    UiMode.PULSE -> PULSE_ASSISTANT_ID
    UiMode.CHAT, UiMode.GROUP -> getCurrentAssistant().id
}

internal fun Settings.ownerForNewConversation(): Assistant? = when (uiMode) {
    UiMode.ROLE -> findCharacter(newConversationOwnerId())?.toAssistant(this)
    UiMode.PULSE -> pulse.toAssistant(this)
    UiMode.CHAT, UiMode.GROUP -> getCurrentAssistant()
}

internal fun Settings.initialChatConversation(id: Uuid): Conversation =
    Conversation.ofId(id, assistantId = newConversationOwnerId() ?: id)

internal fun Settings.newChatConversation(id: Uuid): Conversation? = ownerForNewConversation()?.let { owner ->
    Conversation.ofId(id, assistantId = owner.id, newConversation = true).updateCurrentMessages(owner.presetMessages)
}

internal fun Conversation.workAssistantSelection(settings: Settings): Uuid? =
    assistantId.takeIf { assistantMode(settings) == AssistantMode.WORK }

internal enum class AssistantMode { WORK, ROLE, GROUP, PULSE }

internal fun Conversation.assistantMode(settings: Settings) = when {
    isGroupChat() -> AssistantMode.GROUP
    isPulseChat() -> AssistantMode.PULSE
    isCharacterChat(settings) -> AssistantMode.ROLE
    else -> AssistantMode.WORK
}

internal fun Conversation.uiModeForChatPage(settings: Settings): UiMode = when {
    isGroupChat() -> UiMode.GROUP
    isPulseChat() -> UiMode.PULSE
    isCharacterChat(settings) -> UiMode.ROLE
    else -> UiMode.CHAT
}

internal fun characterMemoryScope(id: Uuid): String = id.toString()
