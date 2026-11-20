package me.rerere.llamacpp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.StreamChunk
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InlineThinkingTest {
    private fun parse(text: String, partial: Boolean = false) =
        JSONObject(splitInlineThinking(JSONObject().put("content", text).toString(), partial))

    @Test fun `native reasoning and ordinary replies are byte identical`() {
        for (json in listOf("not json", """{ "content": "answer" }""",
            """{ "content": "<think>body</think>", "reasoning_content": "native", "tool_calls": [] }""")) {
            assertEquals(json, splitInlineThinking(json, true))
        }
    }

    @Test fun `open block streams reasoning and closed block preserves prefix tools and extra fields`() {
        assertEquals("reason", parse("<think>\nreason", true).getString("reasoning_content"))
        assertEquals("", parse("<think>reason", true).getString("content"))
        val json = JSONObject().put("content", "prefix<thinking>reason</thinking>answer<think>later</think>")
            .put("tool_calls", org.json.JSONArray("""[{"id":"x"}]""")).put("extra", 42)
        val parsed = JSONObject(splitInlineThinking(json.toString()))
        assertEquals("reason", parsed.getString("reasoning_content"))
        assertEquals("prefixanswer<think>later</think>", parsed.getString("content"))
        assertEquals(json.getJSONArray("tool_calls").toString(), parsed.getJSONArray("tool_calls").toString())
        assertEquals(42, parsed.getInt("extra"))
    }

    @Test fun `fenced and inline code examples stay unchanged and later real thinking is split`() {
        for (content in listOf("```xml\n<think>example</think>\n```", "`<think>`", "~~~\n<thinking>x\n~~~")) {
            assertEquals(content, parse(content).getString("content"))
            assertFalse(parse(content).has("reasoning_content"))
        }
        assertEquals("real", parse("`<think>`\n<think>real</think>answer").getString("reasoning_content"))
    }

    @Test fun `single character streaming hides partial delimiters without duplicate text`() = runBlocking {
        for (tag in listOf("think", "thinking")) {
            val source = "<$tag>reason</$tag>answer"
            val generation = object : LocalGeneration {
                override fun generate(onPiece: (String) -> Boolean) { for (char in source) if (!onPiece(char.toString())) return }
                override fun parse(text: String, partial: Boolean) =
                    splitInlineThinking(JSONObject().put("content", text).toString(), partial)
                override fun cancel() = Unit
            }
            val chunks = streamFromLoadedModel(generation, "inline").toList()
            assertEquals("reason", chunks.filterIsInstance<StreamChunk.ReasoningDelta>().joinToString("") { it.text })
            assertEquals("answer", chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
            assertEquals(1, chunks.count { it is StreamChunk.ReasoningStart })
            assertEquals(1, chunks.count { it is StreamChunk.ReasoningEnd })
            assertTrue(chunks.indexOfFirst { it is StreamChunk.ReasoningEnd } < chunks.indexOfFirst { it is StreamChunk.TextDelta })
        }
    }
}
