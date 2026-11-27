package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.st.regex.*
import me.rerere.rikkahub.data.st.macro.MacroStoreRegistry

internal fun applyStRegex(
    message: UIMessage,
    scripts: List<RegexScript>,
    isPrompt: Boolean = false,
    isMarkdown: Boolean = false,
    isEdit: Boolean = false,
    depth: Int? = null,
    substitute: (String) -> String = { it },
): UIMessage {
    val placement = when (message.role) { MessageRole.USER -> RegexPlacement.USER_INPUT; MessageRole.ASSISTANT -> RegexPlacement.AI_OUTPUT; else -> return message }
    return message.copy(parts = message.parts.map { part ->
        if (part is UIMessagePart.Text) part.copy(text = RegexScriptEngine.getRegexedString(part.text, scripts,
            placement, isMarkdown, isPrompt, isEdit, depth, substitute)) else part
    })
}

internal fun applyCharacterMessageRegex(message: UIMessage, character: Character?, settings: Settings, isEdit: Boolean = false,
    conversationId: String? = null, history: List<UIMessage> = emptyList(), registry: MacroStoreRegistry? = null): UIMessage =
    if (character == null) message else applyStRegex(message, mergeActiveRegexScripts(character.stRegexScripts, settings.stRegexScripts),
        isEdit = isEdit, depth = history.indexOfLast { it.id == message.id }.let { if (it < 0) 0 else history.lastIndex - it },
        substitute = characterRegexSubstitute(character, settings, conversationId, history, registry))

/** General scripts already ran before persistence; display-only scripts never alter stored messages. */
internal fun stDisplayText(text: String, character: Character?, settings: Settings, role: MessageRole, loading: Boolean,
    depth: Int? = null, conversationId: String? = null): String {
    if (character == null) return text
    val scripts = mergeActiveRegexScripts(character.stRegexScripts, settings.stRegexScripts)
        .filter { it.markdownOnly || (loading && !it.promptOnly) }
    return RegexScriptEngine.getRegexedString(text, scripts,
        if (role == MessageRole.USER) RegexPlacement.USER_INPUT else RegexPlacement.AI_OUTPUT, isMarkdown = true,
        depth = depth, substitute = characterRegexSubstitute(character, settings, conversationId))
}
