package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.st.macro.*

internal object CharacterMacroBootstrap {
    init { MacroDefinitions.registerAll() }
    fun ensureInitialized() = Unit
}

internal fun characterMacroEnvironment(character: Character, settings: Settings, chat: List<UIMessage>,
    variables: VariableStores, outlets: Map<String, String> = emptyMap()): MacroEnv {
    CharacterMacroBootstrap.ensureInitialized()
    val persona = settings.characterPersonas.firstOrNull { it.id == character.personaId }
    return MacroEnv(
        names = MacroEnv.Names(char = character.nickname.ifBlank { character.name },
            user = persona?.name?.ifBlank { settings.displaySetting.userNickname } ?: settings.displaySetting.userNickname),
        character = MacroEnv.CharacterInfo(description = character.description, personality = character.personality,
            scenario = character.scenario, persona = persona?.description.orEmpty(), charPrompt = character.characterSystemPrompt,
            charInstruction = character.postHistoryInstructions, charDepthPrompt = character.depthPrompt,
            mesExamplesRaw = character.exampleMessages, creatorNotes = character.creatorNotes,
            firstMessage = character.firstMessage, alternateGreetings = character.alternateGreetings, version = character.characterVersion),
        variables = variables,
        chat = chat.map { MacroChatMessage(it.toText(), it.role == MessageRole.USER, it.role == MessageRole.SYSTEM) }, outlets = outlets,
    )
}

/** Rendering uses snapshots: a UI recomposition must never persist set/inc macro side effects. */
internal fun characterRegexSubstitute(character: Character, settings: Settings, conversationId: String? = null,
    chat: List<UIMessage> = emptyList(), registry: MacroStoreRegistry? = null): (String) -> String {
    val localId = conversationId?.let { "conversation:$it" }
    val local = if (localId != null && registry != null) registry.store(localId) { settings.stVariables[localId].orEmpty() }
        else InMemoryMacroVariableStore().apply { restore(settings.stVariables[localId].orEmpty()) }
    val global = registry?.store("global") { settings.stVariables["global"].orEmpty() }
        ?: InMemoryMacroVariableStore().apply { restore(settings.stVariables["global"].orEmpty()) }
    val env = characterMacroEnvironment(character, settings, chat, VariableStores(local, global))
    return { MacroEngine.evaluate(it, env) }
}
