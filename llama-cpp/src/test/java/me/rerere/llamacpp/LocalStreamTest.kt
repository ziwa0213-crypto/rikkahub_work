package me.rerere.llamacpp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.core.TokenUsage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LocalStreamTest {
    @Test fun `normal empty or reasoning only output fails without finish but tool only is valid`() = runBlocking {
        listOf("{}", """{"content":"  ","reasoning_content":"thinking"}""",
            """{"tool_calls":[{"function":{"name":"missing_arguments"}}]}""").forEach { parsed ->
            val chunks = mutableListOf<StreamChunk>()
            val failure = runCatching {
                streamFromLoadedModel(Generation(0) { _, _ -> parsed }, "empty", "nCtx=4096").collect { chunks += it }
            }.exceptionOrNull() as LocalModelException
            assertEquals(LocalModelError.EMPTY_OUTPUT, failure.reason)
            assertFalse(chunks.any { it is StreamChunk.Finish })
        }
        val tool = Generation(0) { _, _ -> """{"tool_calls":[{"id":"a","function":{"name":"time","arguments":"{}"}}]}""" }
        assertTrue(streamFromLoadedModel(tool, "tool", "nCtx=4096").toList().last() is StreamChunk.Finish)
    }
    private class Generation(val pieces: Int, val parseBlock: (String, Boolean) -> String) : LocalGeneration {
        val cancelled = AtomicBoolean()
        val partial = AtomicInteger()
        val terminal = AtomicInteger()
        override fun generate(onPiece: (String) -> Boolean) { repeat(pieces) { if (!onPiece("文")) return } }
        override fun parse(text: String, partial: Boolean): String {
            if (partial) this.partial.incrementAndGet() else terminal.incrementAndGet()
            return parseBlock(text, partial)
        }
        override fun cancel() { cancelled.set(true) }
    }

    @Test fun `slow collector loses neither text beyond 64 slots nor terminal chunks`() = runBlocking {
        val source = Generation(512) { text, _ -> JSONObject().put("content", text).toString() }
        val chunks = mutableListOf<StreamChunk>()
        streamFromLoadedModel(source, "test").collect { delay(1); chunks += it }
        assertEquals("文".repeat(512), chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        assertEquals(512, source.partial.get())
        assertEquals(1, source.terminal.get())
        assertEquals(1, chunks.count { it is StreamChunk.TextStart })
        assertEquals(1, chunks.count { it is StreamChunk.TextEnd })
        assertTrue(chunks.last() is StreamChunk.Finish)
        assertFalse(chunks.filterIsInstance<StreamChunk.TextDelta>().any { it.text.isEmpty() })
    }

    @Test fun `authoritative parse flushes incomplete tools exactly once without empty text parts`() = runBlocking {
        val source = Generation(100) { _, partial ->
            if (partial) """{"content":"","tool_calls":[{"id":"a","function":{"name":"lookup","arguments":"{"}}]}"""
            else """{"tool_calls":[{"id":"a","function":{"name":"lookup","arguments":"{\"q\":\"x\""}}]}"""
        }
        val chunks = streamFromLoadedModel(source, "test").toList()
        assertEquals(1, chunks.count { it is StreamChunk.ToolCallStart })
        assertEquals("{\"q\":\"x\"", chunks.filterIsInstance<StreamChunk.ToolCallDelta>().single().inputDelta)
        assertEquals(1, chunks.count { it is StreamChunk.ToolCallEnd })
        assertFalse(chunks.any { it is StreamChunk.TextStart || it is StreamChunk.TextDelta })
        assertTrue(chunks.last() is StreamChunk.Finish)
    }

    @Test fun `reasoning ends before body and repeat whole parses do not duplicate calls`() = runBlocking {
        val source = Generation(80) { text, _ ->
            JSONObject().put("reasoning_content", "reason").put("content", if (text.length > 1) "answer" else "")
                .put("tool_calls", org.json.JSONArray("""[{"id":"x","function":{"name":"time","arguments":"{}"}}]""")).toString()
        }
        val chunks = streamFromLoadedModel(source, "test").toList()
        assertEquals("reason", chunks.filterIsInstance<StreamChunk.ReasoningDelta>().joinToString("") { it.text })
        assertEquals("answer", chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        assertTrue(chunks.indexOfFirst { it is StreamChunk.ReasoningEnd } < chunks.indexOfFirst { it is StreamChunk.TextDelta })
        assertEquals(1, chunks.count { it is StreamChunk.ToolCallStart })
    }

    @Test fun `collector cancellation interrupts blocked generation and emits no normal finish`() = runBlocking {
        val entered = AtomicBoolean()
        val stopped = AtomicBoolean()
        val chunks = mutableListOf<StreamChunk>()
        val generation = object : LocalGeneration {
            override fun generate(onPiece: (String) -> Boolean) {
                onPiece("one"); entered.set(true)
                while (!stopped.get()) Thread.sleep(1)
            }
            override fun parse(text: String, partial: Boolean) = JSONObject().put("content", text).toString()
            override fun cancel() { stopped.set(true) }
        }
        val job = launch { streamFromLoadedModel(generation, "stop").collect { chunks += it } }
        withTimeout(3000) { while (!entered.get()) delay(1) }
        withTimeout(3000) { job.cancelAndJoin() }
        assertTrue(stopped.get())
        assertFalse(chunks.any { it is StreamChunk.TextEnd || it is StreamChunk.Finish })
    }

    @Test fun `parser failure reaches collector without a fabricated finish`() = runBlocking {
        val source = Generation(1) { _, _ -> error("invalid parser") }
        val chunks = mutableListOf<StreamChunk>()
        val failure = runCatching { streamFromLoadedModel(source, "bad").collect { chunks += it } }.exceptionOrNull()
        assertEquals("invalid parser", failure?.message)
        assertFalse(chunks.any { it is StreamChunk.Finish })
    }

    @Test fun `speculative reset is suppressed and suffix resumes at corrected watermark`() {
        val tracker = ChatDeltaTracker()
        val watermark = ChannelWatermark()
        fun next(value: String): String {
            val delta = tracker.consume(JSONObject().put("content", value).toString())
            return watermark.advance(delta.textDelta, delta.textReset, false)
        }
        assertEquals("prefix", next("prefix"))
        assertEquals("", next("corrected"))
        assertEquals(" suffix", next("corrected suffix"))
    }

    @Test fun `usage counts full raw generation once before finish including tool only output`() = runBlocking {
        var counted: String? = null
        var calls = 0
        val generation = object : LocalGeneration {
            override fun generate(onPiece: (String) -> Boolean) { onPiece("<tool_"); onPiece("call>raw") }
            override fun parse(text: String, partial: Boolean) = """{"tool_calls":[{"id":"a","function":{"name":"lookup","arguments":"{}"}}]}"""
            override fun cancel() = Unit
            override fun usage(producedText: String): TokenUsage {
                calls++; counted = producedText
                return TokenUsage(promptTokens = 600, completionTokens = 7, totalTokens = 607)
            }
        }
        val chunks = streamFromLoadedModel(generation, "usage").toList()
        assertEquals("<tool_call>raw", counted); assertEquals(1, calls)
        assertEquals(607, chunks.filterIsInstance<StreamChunk.Usage>().single().usage.totalTokens)
        assertTrue(chunks[chunks.lastIndex - 1] is StreamChunk.Usage)
        assertTrue(chunks.last() is StreamChunk.Finish)
    }
}
