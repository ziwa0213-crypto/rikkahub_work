package me.rerere.ai.provider.providers.deepseekweb

import kotlin.test.Test
import kotlin.test.assertEquals
import me.rerere.ai.ui.StreamChunk

class DeepSeekWebSSETest {
    @Test
    fun parsesSnapshotAndFragmentPatches() {
        val decoder = DeepSeekWebSSE("response-1", "deepseek-web")
        val first = decoder.accept(
            """{"v":{"response":{"fragments":[{"type":"THINK","content":"先想"},{"type":"RESPONSE","content":"你好"}]}}}"""
        )
        val second = decoder.accept(
            """{"p":"response/fragments","o":"APPEND","v":{"type":"RESPONSE","content":"，世界"}}"""
        )
        val third = decoder.accept(
            """{"p":"response/status","v":"FINISHED"}"""
        )
        assertEquals(listOf("先想"), first.filterIsInstance<StreamChunk.ReasoningDelta>().map { it.text })
        assertEquals(listOf("你好", "，世界"), (first + second).filterIsInstance<StreamChunk.TextDelta>().map { it.text })
        assertEquals(1, decoder.finish().count { it is StreamChunk.Finish })
        assertEquals(0, decoder.finish().count { it is StreamChunk.Finish })
        assertEquals(1, third.size)
    }

    @Test
    fun parsesMultilineJsonEvent() {
        val decoder = DeepSeekWebSSE("response-2", "deepseek-web")
        val chunks = decoder.accept("""{"v":{"response":
            {"content":"多行"}}}""")
        assertEquals("多行", chunks.filterIsInstance<StreamChunk.TextDelta>().single().text)
    }

    @Test
    fun emitsOnlyAllowedToolCalls() {
        val decoder = DeepSeekWebSSE("response-3", "deepseek-web")
        decoder.accept("""{"p":"response/content","v":"{\"tool\":\"workspace_shell\",\"arguments\":{}}"}""")
        val chunks = decoder.finish()
        assertEquals(0, chunks.count { it is StreamChunk.ToolCallStart })
        assertEquals(1, chunks.filterIsInstance<StreamChunk.TextDelta>().size)
    }
}
