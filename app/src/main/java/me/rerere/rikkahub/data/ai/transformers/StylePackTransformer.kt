package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.StylePack

internal fun applyCharacterInjections(messages: List<UIMessage>, character: Character?, packs: List<StylePack>, heading: String): List<UIMessage> {
    character ?: return messages
    val injections = buildList<PromptInjection> {
        val stylePack = packs.firstOrNull { it.id == character.stylePackId && it.enabled }
        if (character.styleNotesEnabled && stylePack != null) {
            val rules = stylePack.rules.filter(String::isNotBlank)
            if (rules.isNotEmpty()) add(PromptInjection.ModeInjection(
                position = InjectionPosition.AT_DEPTH,
                injectDepth = stylePack.injectDepth.coerceAtLeast(1),
                role = MessageRole.USER,
                content = "## $heading\n" + rules.joinToString("\n") { "- $it" },
            ))
        }
        if (character.postHistoryInstructions.isNotBlank()) add(PromptInjection.ModeInjection(
            position = InjectionPosition.BOTTOM_OF_CHAT, role = MessageRole.SYSTEM, content = character.postHistoryInstructions.trim(),
        ))
    }
    return if (injections.isEmpty()) messages else applyInjections(messages, injections.groupBy { it.position })
}
