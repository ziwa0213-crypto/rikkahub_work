package me.rerere.llamacpp

import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContextAndRequestTest {
    @Test fun `GQA KV formula uses KV heads with both key and value dimensions`() {
        val info = """{"n_layers":32,"n_head_kv":8,"n_embd_head_k":128,"n_embd_head_v":128,"n_ctx_train":131072}"""
        val plan = ContextPlanner.plan(info, 1_000_000_000, 32_000_000_000)
        assertEquals(16384, plan.nCtx)
        assertEquals(32L * 8 * 256 * 2 * 16384, plan.kvBytes)
        assertEquals(8192, ContextPlanner.plan(info, 1_000_000_000, 32_000_000_000, 8192).nCtx)
    }

    @Test fun `low available memory warns without shrinking selected context`() {
        val info = """{"n_layers":32,"n_head_kv":8,"n_embd_head_k":128,"n_embd_head_v":128,"n_ctx_train":16384}"""
        val plan = ContextPlanner.plan(info, 7_360_000_000, 6_000_000_000)
        assertEquals(16384, plan.nCtx)
        assertTrue(plan.tight)
        assertEquals(4096, ContextPlanner.plan(info, 7_360_000_000, 0, 4096).nCtx)
        assertEquals(7_360_000_000L / 4 + plan.kvBytes + 512L * 1024 * 1024, plan.estimatedBytes)
    }

    @Test fun `hybrid per layer KV heads use largest positive head count`() {
        val info = """{"n_layers":24,"n_head_kv":[0,0,4,0,4],"n_embd_head_k":128,"n_embd_head_v":128,"n_ctx_train":262144}"""
        val plan = ContextPlanner.plan(info, 0, 0)
        assertEquals(24L * 4 * 256 * 2 * 16384, plan.kvBytes)
        assertEquals(16384, plan.nCtx)
    }

    @Test fun `output never consumes more than one quarter of supported window`() {
        assertEquals(1024, ContextPlanner.outputBudget(4096, null))
        assertEquals(2048, ContextPlanner.outputBudget(16384, null))
        assertEquals(128, ContextPlanner.outputBudget(512, 20000))
        assertEquals(1, ContextPlanner.outputBudget(4096, 0))
        assertEquals(64, ContextPlanner.outputBudget(128, 2048))
    }

    @Test fun `trimming keeps system and latest user and never separates tools from their turn`() {
        val system = UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text("rules")))
        val oldest = UIMessage.user("old")
        val tools = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
            UIMessagePart.Tool("a", "lookup", "{}", listOf(UIMessagePart.Text("result"))),
            UIMessagePart.Text("old answer")))
        val newest = UIMessage.user("new")
        val history = listOf(system, oldest, tools, newest)
        val trimmed = ContextPlanner.trim(history, 2) { it.size }
        assertEquals(listOf(system, newest), trimmed)
        assertEquals(4, history.size)
    }

    @Test fun `oversized newest user fails explicitly rather than truncating input`() {
        val message = UIMessage.user("x".repeat(1000))
        val error = runCatching { ContextPlanner.trim(listOf(message), 20) { 1000 } }.exceptionOrNull()
        assertEquals(LocalModelError.TOO_LONG, (error as LocalModelException).reason)
        assertEquals(1000, (message.parts.single() as UIMessagePart.Text).text.length)
    }

    @Test fun `two tool rounds retain call result and following text in order`() {
        val assistant = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
            UIMessagePart.Text("first"), UIMessagePart.Reasoning("why"),
            UIMessagePart.Tool("a", "lookup", "{\"q\":\"世界🌍\"}", listOf(UIMessagePart.Text("one"))),
            UIMessagePart.Text("second"),
            UIMessagePart.Tool("b", "clock", "{}", listOf(UIMessagePart.Text("two"))),
            UIMessagePart.Text("done")))
        val params = TextGenerationParams(Model(), tools = listOf(Tool("lookup", "description", parameters = {
            InputSchema.Obj(buildJsonObject { put("q", buildJsonObject { put("type", "string") }) }, listOf("q"))
        }, execute = { emptyList() })))
        val request = JSONObject(ChatRequestMapper.toRequestJson(listOf(UIMessage.user("ask"), assistant), params))
        val messages = request.getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "tool", "assistant", "tool", "assistant"),
            (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
        assertEquals("why", messages.getJSONObject(1).getString("reasoning_content"))
        assertEquals("a", messages.getJSONObject(2).getString("tool_call_id"))
        assertEquals("b", messages.getJSONObject(4).getString("tool_call_id"))
        assertTrue(messages.getJSONObject(1).getJSONArray("tool_calls").toString().contains("世界🌍"))
        assertEquals("q", request.getJSONArray("tools").getJSONObject(0).getJSONObject("function")
            .getJSONObject("parameters").getJSONArray("required").getString(0))
    }

    @Test fun `pending approvals do not fabricate tool results`() {
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Tool("a", "wait", "{}")))
        val request = JSONObject(ChatRequestMapper.toRequestJson(listOf(message), TextGenerationParams(Model())))
        assertEquals(0, request.getJSONArray("messages").length())
        assertFalse(request.has("tools"))
    }
}
