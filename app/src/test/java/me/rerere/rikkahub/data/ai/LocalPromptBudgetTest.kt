package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.Tool
import me.rerere.ai.provider.LocalPromptDrop
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.prompts.buildRowMemoryPrompt
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.RowMemory
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class LocalPromptBudgetTest {
    private val messages = listOf(UIMessage.user("你好"))
    private fun tool(prompt: String = "skills", description: String = "lookup") =
        Tool("use_skill", description, systemPrompt = { _, _ -> prompt }, execute = { emptyList() })
    private fun turn(
        context: Int = 4096,
        system: String = "persona",
        memory: Boolean = false,
        tools: List<Tool> = listOf(tool()),
        rows: List<RowMemory> = emptyList(),
        files: List<MemoryFile> = emptyList(),
        provider: ProviderSetting = ProviderSetting.LlamaCppLocal(),
        conversation: String? = null,
    ) = ChatTurnContext(Settings(), Assistant(systemPrompt = system, enableMemory = memory,
        allowConversationSystemPrompt = true), Model(contextLength = context), provider, Uuid.random(),
        conversation, null, files, tools, rowMemories = rows)

    @Test fun `4K small prompt keeps tools and original text`() {
        val turn = turn()
        val result = buildBudgetedSystemPrompt(turn, messages)
        assertTrue(result.dropped.isEmpty())
        assertSame(turn.tools, result.tools)
        assertEquals("persona\nskills", result.system)
    }

    @Test fun `4K drops definitions and skill prompt together without truncating persona`() {
        val turn = turn(system = "人格".repeat(2500), tools = listOf(tool("skill-list")))
        val result = buildBudgetedSystemPrompt(turn, messages)
        assertEquals(listOf(LocalPromptDrop.TOOLS), result.dropped)
        assertTrue(result.tools.isEmpty())
        assertEquals(turn.assistant.systemPrompt, result.system)
        assertEquals("你好", messages.single().toText())
    }

    @Test fun `large schema alone counts toward tool downgrade`() {
        val result = buildBudgetedSystemPrompt(turn(tools = listOf(tool("", "schema".repeat(1200)))), messages)
        assertEquals(listOf(LocalPromptDrop.TOOLS), result.dropped)
        assertFalse(result.system.contains("schema"))
    }

    @Test fun `large memory drops only after tools and never changes stored data`() {
        val row = RowMemory(1, "remembered-row".repeat(1600))
        val file = MemoryFile(MemoryFile.PREFERENCES_PATH, "remembered-file".repeat(1600), 0)
        val turn = turn(memory = true, rows = listOf(row), files = listOf(file))
        val result = buildBudgetedSystemPrompt(turn, messages)
        assertEquals(listOf(LocalPromptDrop.TOOLS, LocalPromptDrop.MEMORY), result.dropped)
        assertEquals("persona", result.system)
        assertEquals(row, turn.rowMemories.single())
        assertEquals(file, turn.memories.single())
    }

    @Test fun `disabled tools are not falsely reported and 8K normal prompt keeps all layers`() {
        val memoryOnly = buildBudgetedSystemPrompt(turn(memory = true, tools = emptyList(),
            rows = listOf(RowMemory(1, "x".repeat(15000)))), messages)
        assertEquals(listOf(LocalPromptDrop.MEMORY), memoryOnly.dropped)
        val normal = buildBudgetedSystemPrompt(turn(context = 8192, memory = true), messages)
        assertEquals(emptyList<LocalPromptDrop>(), normal.dropped)
        assertTrue(normal.system.contains("<memory_system>"))
        assertTrue(normal.system.contains("skills"))
        assertEquals(1, normal.tools.size)
    }

    @Test fun `budget uses actual loaded window rather than saved training ceiling`() {
        val turn = turn(context = 16384, tools = listOf(tool("x".repeat(6000))))
        assertTrue(buildBudgetedSystemPrompt(turn, messages).dropped.isEmpty())
        assertEquals(listOf(LocalPromptDrop.TOOLS), buildBudgetedSystemPrompt(turn, messages, 4096).dropped)
    }

    @Test fun `API system bytes and tools match legacy assembly even with tiny context`() {
        for (provider in listOf(ProviderSetting.OpenAI(), ProviderSetting.Google(),
            ProviderSetting.Claude(), ProviderSetting.DeepSeekWeb())) {
            var calls = 0
            val tools = listOf(Tool("api-tool", "api", parameters = { error("API must not estimate schemas") },
                systemPrompt = { _, _ -> calls++; "\n API-tool \n" }, execute = { emptyList() }))
            val turn = turn(context = 512, system = "ignored", memory = true, tools = tools,
                rows = listOf(RowMemory(7, "row")), files = listOf(MemoryFile("/profile.md", "file", 0)),
                provider = provider, conversation = "  conversation override\n").let { original ->
                ChatTurnContext(original.settings, original.assistant, original.model, original.providerSetting,
                    original.conversationId, original.conversationSystemPrompt, null, original.memories,
                    original.tools, rowMemories = original.rowMemories, pulseInstruction = "pulse instruction")
            }
            val expected = buildString {
                append("  conversation override\n")
                appendPulseInstruction(this, "pulse instruction")
                appendLine()
                append(buildRowMemoryPrompt(turn.rowMemories))
                append(buildMemoryPrompt(turn.memories))
                appendLine()
                append("\n API-tool \n")
            }
            val actual = buildBudgetedSystemPrompt(turn, messages)
            assertArrayEquals(expected.toByteArray(), actual.system.toByteArray())
            assertSame(tools, actual.tools)
            assertTrue(actual.dropped.isEmpty())
            assertEquals(1, calls)
        }
    }
}
