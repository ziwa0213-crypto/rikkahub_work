package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.prompts.buildRowMemoryPrompt
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.RowMemory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ApiSystemPromptTest {
    @Test fun apiPrefixRetainsAllLayersEvenWithTinyContext() {
        for (provider in listOf(ProviderSetting.OpenAI(), ProviderSetting.Google(),
            ProviderSetting.Claude(), ProviderSetting.DeepSeekWeb())) {
            var calls = 0
            val tools = listOf(Tool("api-tool", "api", parameters = { error("Do not estimate API schemas") },
                systemPrompt = { _, _ -> calls++; "\n API-tool \n" }, execute = { emptyList() }))
            val turn = ChatTurnContext(
                Settings(), Assistant(systemPrompt = "ignored", enableMemory = true, allowConversationSystemPrompt = true),
                Model(contextLength = 512), provider, Uuid.random(), "  conversation override\n", null,
                listOf(MemoryFile("/profile.md", "file".repeat(1000), 0)), tools,
                rowMemories = listOf(RowMemory(7, "row".repeat(1000))), pulseInstruction = "pulse instruction",
            )
            val expected = buildString {
                append("  conversation override\n")
                appendPulseInstruction(this, "pulse instruction")
                appendLine()
                append(buildRowMemoryPrompt(turn.rowMemories))
                append(buildMemoryPrompt(turn.memories))
                appendLine()
                append("\n API-tool \n")
            }
            assertArrayEquals(expected.toByteArray(), buildSystemPrompt(turn, listOf(UIMessage.user("hello"))).toByteArray())
            assertEquals(1, calls)
        }
    }
}
