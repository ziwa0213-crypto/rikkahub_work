package me.rerere.rikkahub.data.model

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.*
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.uuid.Uuid

class CharacterAssistantParityTest {
    private val special = setOf("tags", "systemPrompt", "useGlobalMemory", "memoryGroupId",
        "allowConversationPromptInjection", "presetMessages")

    @Test fun `every assistant property has an explicit role policy`() {
        val direct = setOf("id", "name", "avatar", "chatModelId", "useAssistantAvatar", "temperature", "topP",
            "contextMessageLimit", "streamOutput", "enableMemory", "autoConsolidateMemory", "enableRecentChatsReference",
            "messageTemplate", "quickMessageIds", "regexes", "reasoningLevel", "toolApprovalMode", "maxTokens",
            "customHeaders", "customBodies", "mcpServers", "localTools", "enableWebSearch", "workspaceId",
            "background", "backgroundOpacity", "useGradientBackground", "modeInjectionIds", "lorebookIds",
            "enabledSkills", "enableTimeReminder", "timeReminderIntervalMinutes", "allowConversationSystemPrompt")
        val actual = Assistant::class.memberProperties.associateBy { it.name }
        assertEquals(actual.keys, direct + special)
        val id = Uuid.random()
        val persona = CharacterPersona(name = "User", description = "persona detail")
        val character = Character(name = "role", chatModelId = id, personaId = persona.id,
            characterSystemPrompt = "role identity", exampleMessages = "example", temperature = 0.4f, topP = 0.7f,
            contextMessageLimit = 25, streamOutput = false, enableMemory = true, autoConsolidateMemory = false,
            enableRecentChatsReference = true, messageTemplate = "template", quickMessageIds = setOf(id),
            regexes = listOf(AssistantRegex(id)), reasoningLevel = ReasoningLevel.HIGH, toolApprovalMode = ToolApprovalMode.AskAll,
            maxTokens = 128, mcpServers = setOf(id), localTools = listOf(LocalToolOption.TimeInfo), enableWebSearch = true,
            workspaceId = id, background = "image", backgroundOpacity = 0.4f, useGradientBackground = true,
            modeInjectionIds = setOf(id), lorebookIds = setOf(id), enabledSkills = setOf("skill"), enableTimeReminder = true,
            timeReminderIntervalMinutes = 8, allowConversationSystemPrompt = true, firstMessage = "greeting")
        val assistant = character.toAssistant(Settings(characterPersonas = listOf(persona)))
        val source = Character::class.memberProperties.associateBy { it.name }
        for (field in direct) assertEquals(field, source.getValue(field).get(character), actual.getValue(field).get(assistant))
        assertTrue(listOf("role identity", "persona detail", "example").all { it in assistant.systemPrompt })
        assertEquals("greeting", assistant.presetMessages.single().toText())
        assertFalse(assistant.useGlobalMemory || assistant.allowConversationPromptInjection)
        assertNull(assistant.memoryGroupId)
        assertTrue(assistant.tags.isEmpty())
        val preset = UIMessage.assistant("explicit")
        assertEquals(listOf(preset), character.copy(presetMessages = listOf(preset)).toAssistant(Settings()).presetMessages)
    }
}
