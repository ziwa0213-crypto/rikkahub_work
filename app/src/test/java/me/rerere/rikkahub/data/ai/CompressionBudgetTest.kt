package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.insertContextCheckpoint
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class CompressionBudgetTest {
    private fun assistant(vararg parts: UIMessagePart) = UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList())
    private fun answer(text: String) = assistant(UIMessagePart.Text(text))

    @Test fun `four local tiers include their exact upper boundaries`() {
        assertEquals(CompressionBudget(1500, 300, .65f), CompressionBudgetRules.forLocal(4096))
        assertEquals(CompressionBudget(3000, 500, .70f), CompressionBudgetRules.forLocal(8192))
        assertEquals(CompressionBudget(6000, 800, .75f), CompressionBudgetRules.forLocal(16384))
        assertEquals(CompressionBudget(12000, 1200, .80f), CompressionBudgetRules.forLocal(16385))
        assertEquals(3000, CompressionBudgetRules.forLocal(4097).inputTokens)
        assertEquals(6000, CompressionBudgetRules.forLocal(8193).inputTokens)
    }

    @Test fun `remote budgets do not constrain input output or global threshold`() {
        assertEquals(CompressionBudget(Int.MAX_VALUE, null, null), CompressionBudgetRules.of(4096, false))
    }

    @Test fun `window includes complete tools and keeps first oversized turn`() {
        val tool = UIMessagePart.Tool("call", "read", "{}", listOf(UIMessagePart.Text("result")))
        val messages = listOf(UIMessage.user("a".repeat(800)), assistant(tool), answer("done"), UIMessage.user("second"), answer("b".repeat(800)))
        assertEquals(listOf(3, 5), compressionBoundaries(messages, -1, messages.size))
        assertEquals(3, selectCompressionWindow(messages, -1, messages.size, 20))
        assertEquals(5, selectCompressionWindow(messages, -1, messages.size, 2000))
    }

    @Test fun `old replaced history does not consume window budget but its checkpoint does`() {
        val summary = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val messages = listOf(UIMessage.user("x".repeat(100_000)), answer("old"), summary, UIMessage.user("new"), answer("new"), UIMessage.user("z".repeat(1000)))
        assertEquals(5, selectCompressionWindow(messages, 2, messages.size, 30))
        assertEquals(listOf(summary) + messages.subList(3, 5), prepareLocalCompressionMessages(messages.take(5)))
    }

    @Test fun `tool clipping retains head tail IDs media and original history`() {
        val original = "a".repeat(800) + "b".repeat(6800) + "z".repeat(400)
        val image = UIMessagePart.Image("file:///private/image")
        val tool = UIMessagePart.Tool("id", "read", "{\"path\":\"x\"}", listOf(UIMessagePart.Text(original), image))
        val message = assistant(tool)
        val result = prepareLocalCompressionMessages(listOf(message, assistant())).single().getTools().single()
        val text = result.output.filterIsInstance<UIMessagePart.Text>().single().text
        assertTrue(text.startsWith("a".repeat(800)))
        assertTrue(text.endsWith("z".repeat(400)))
        assertTrue(text.contains("[truncated 6800 characters]"))
        assertEquals(listOf(image), result.output.filterIsInstance<UIMessagePart.Image>())
        assertEquals(tool.input, result.input); assertEquals(tool.toolCallId, result.toolCallId)
        assertEquals(original, (message.getTools().single().output.first() as UIMessagePart.Text).text)
    }

    @Test fun `native validation shrinks only at user boundaries or fails without swallowing history`() = runBlocking {
        val checked = mutableListOf<Int>()
        assertEquals(4, fitCompressionWindow(listOf(2, 4, 6), 6) { checked += it; it <= 4 })
        assertEquals(listOf(6, 4), checked)
        assertNull(fitCompressionWindow(listOf(2, 4), 4) { false })
    }

    @Test fun `two partial checkpoints preserve every uncompressed node and source message`() {
        val messages = (1..5).flatMap { listOf(UIMessage.user("question $it"), answer("answer $it")) }
        val original = Conversation(assistantId = Uuid.random(), messageNodes = messages.map { it.toMessageNode() })
        val first = original.insertContextCheckpoint(original.messageNodes[1].id, "S1")!!
        assertEquals(original.messageNodes.takeLast(8), first.messageNodes.takeLast(8))
        val next = first.insertContextCheckpoint(original.messageNodes[3].id, "S2")!!
        assertEquals(original.messageNodes.takeLast(6), next.messageNodes.takeLast(6))
        assertEquals(messages.map { it.id }, next.currentMessages.filterNot { it.isContextCheckpoint }.map { it.id })
        assertEquals("S2", prepareLocalCompressionMessages(next.currentMessages).first().toText())
    }

    @Test fun `length instruction only appends to existing seven section template`() {
        val api = buildCompressPrompt("exact path /a", "English", true)
        val local = buildCompressPrompt("exact path /a", "English", true, 1600)
        assertEquals(api, local.substringBefore("\n\nLength limit:"))
        assertTrue(local.endsWith("Preserve identifiers, file paths, error codes and version numbers verbatim instead of paraphrasing them."))
        assertEquals(7, Regex("(?m)^## ").findAll(api).count())
        assertFalse(buildCompressPrompt("", "English").contains("Length limit:"))
    }

    @Test fun `large history selects one bounded prefix not the whole backlog`() {
        val messages = (1..1500).flatMap { listOf(UIMessage.user("文".repeat(80)), answer("text".repeat(40))) }
        val end = selectCompressionWindow(messages, -1, messages.size, 6000)
        assertTrue(end in 2 until messages.size)
        assertEquals(MessageRole.USER, messages[end].role)
        assertTrue(estimateLocalTokens(prepareLocalCompressionMessages(messages.take(end))) <= 6000)
    }
}
