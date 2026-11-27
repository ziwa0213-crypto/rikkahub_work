package me.rerere.rikkahub.data.st.worldbook

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.*
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.*
import org.junit.Test

class StInjectionInserterTest {
    @Test fun systemRolesAndDepthArePreserved() {
        val history = listOf(UIMessage.system("system"), UIMessage.user("1"), UIMessage.assistant("2"), UIMessage.user("3"))
        val worldbook = PromptInjection.RegexInjection(content = "book", role = MessageRole.SYSTEM, injectDepth = 1)
        val inserted = insertKeepingRole(history, mapOf(InjectionPosition.AT_DEPTH to listOf(worldbook)))
        assertEquals(MessageRole.SYSTEM, inserted.first { it.toText() == "book" }.role)
        val result = applyCharacterDepthPrompt(inserted, Character(depthPrompt = "depth", depthPromptDepth = 2))
        assertEquals(inserted.size - 2, result.indexOfFirst { it.toText() == "depth" })
        assertEquals(MessageRole.SYSTEM, result.first { it.toText() == "depth" }.role)
        for (role in listOf("assistant", "user")) {
            val message = applyCharacterDepthPrompt(history, Character(depthPrompt = "depth", depthPromptRole = role)).first { it.toText() == "depth" }
            assertEquals(role.uppercase(), message.role.name)
            assertTrue(message.isSynthetic)
        }
        assertEquals(history, applyCharacterDepthPrompt(history, Character()))
        assertEquals(history, insertKeepingRole(history, emptyMap()))
    }
}
