package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(0, third.size)
    }

    @Test
    fun parsesMultilineJsonEvent() {
        val decoder = DeepSeekWebSSE("response-2", "deepseek-web")
        val chunks = decoder.accept("""{"v":{"response":
            {"content":"多行"}}}""")
        assertEquals("多行", chunks.filterIsInstance<StreamChunk.TextDelta>().single().text)
    }

    @Test
    fun rejectsBlockedToolCallsAsCapabilityErrors() {
        val decoder = DeepSeekWebSSE("response-3", "deepseek-web")
        decoder.accept("""{"p":"response/content","v":"{\"tool\":\"workspace_shell\",\"arguments\":{}}"}""")
        val chunks = decoder.finish()
        assertEquals("workspace_shell", decoder.refusal!!.blockedToolName)
        assertTrue(chunks.none { it is StreamChunk.ToolCallStart })
    }

    @Test
    fun acceptsArrayFragmentPatchesInThinkingMode() {
        val decoder = DeepSeekWebSSE("response-4", "deepseek-web-thinking", thinkingEnabled = true)
        val chunks = decoder.accept(
            """{"p":"response/fragments","o":"APPEND","v":[{"type":"THINK","content":"先想"},{"type":"RESPONSE","content":"答案"}]}"""
        )
        assertEquals(listOf("先想"), chunks.filterIsInstance<StreamChunk.ReasoningDelta>().map { it.text })
        assertEquals(listOf("答案"), chunks.filterIsInstance<StreamChunk.TextDelta>().map { it.text })
        assertEquals(1, decoder.finish().count { it is StreamChunk.Finish })
    }

    @Test
    fun keepsAllFastModeContentAcrossPatchesAndFinishStatus() {
        val decoder = DeepSeekWebSSE("response-5", "deepseek-web")
        val first = decoder.accept("""{"p":"response/content","v":"第一段"}""")
        val second = decoder.accept("""{"p":"response/content","v":"第二段"}""")
        decoder.accept("""{"p":"response/status","v":"FINISHED"}""")
        val third = decoder.accept("""{"p":"response/content","v":"第三段"}""")
        val finish = decoder.finish()

        assertEquals(listOf("第一段", "第二段", "第三段"), (first + second + third).filterIsInstance<StreamChunk.TextDelta>().map { it.text })
        assertEquals("FINISHED", finish.filterIsInstance<StreamChunk.Finish>().single().finishReason)
    }

    @Test
    fun acceptsBatchedResponseArray() {
        val decoder = DeepSeekWebSSE("response-6", "deepseek-web")
        val chunks = decoder.accept(
            """{"p":"response","o":"BATCH","v":[{"p":"fragments","o":"APPEND","v":[{"type":"RESPONSE","content":"批量正文"}]}]}"""
        )
        assertEquals(listOf("批量正文"), chunks.filterIsInstance<StreamChunk.TextDelta>().map { it.text })
    }

    @Test
    fun buffersDsmlEvenWhenMarkerIsSplitAcrossEveryEvent() {
        val content = "prefix" + """<||DSML||calls><||DSML||invoke name="ask_user"></||DSML||invoke></||DSML||calls>"""
        val decoder = DeepSeekWebSSE("dsml", "deepseek-web")
        val streamed = content.map { char -> decoder.accept(event(char.toString())) }.flatten()
        assertEquals("prefix", streamed.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        val finished = decoder.finish()
        assertEquals(content, (streamed + finished).filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        assertTrue(finished.none { it is StreamChunk.ToolCallStart })
    }

    @Test
    fun keepsOrdinaryLessThanTextAndJsonToolCallsWorking() {
        val decoder = DeepSeekWebSSE("less-than", "deepseek-web")
        val chunks = decoder.accept(event("2 <")) + decoder.accept(event(" 3")) + decoder.finish()
        assertEquals("2 < 3", chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        val tools = DeepSeekWebSSE("tools", "deepseek-web")
        tools.accept(event("""{"tool":"calendar_query","arguments":{}}"""))
        assertEquals("calendar_query", tools.finish().filterIsInstance<StreamChunk.ToolCallStart>().single().toolName)
    }

    @Test
    fun convertsFencedCallsWithProseEvenWhenEveryCharacterIsASeparateEvent() {
        for (thinking in listOf(false, true)) {
            val content = "before```json\n{\"tool\":\"calendar_query\",\"arguments\":{\"range\":\"week\",\"limit\":5}}\n```after"
            val decoder = DeepSeekWebSSE("split", "deepseek-web", thinkingEnabled = thinking)
            val streamed = content.map { decoder.accept(event(it.toString())) }.flatten()
            assertEquals("before", streamed.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
            val finished = decoder.finish()
            assertEquals("beforeafter", (streamed + finished).filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
            assertEquals("calendar_query", finished.filterIsInstance<StreamChunk.ToolCallStart>().single().toolName)
            assertEquals("""{"range":"week","limit":5}""", finished.filterIsInstance<StreamChunk.ToolCallDelta>().single().inputDelta)
            assertEquals("tool_calls", finished.filterIsInstance<StreamChunk.Finish>().single().finishReason)
        }
    }

    @Test
    fun convertsOnlyFirstCallAndDoesNotLeakLaterCalls() {
        val content = "before{\"tool\":\"get_time_info\",\"arguments\":{}}between{\"tool\":\"calendar_delete\",\"arguments\":{}}after"
        val decoder = DeepSeekWebSSE("multiple", "deepseek-web")
        val chunks = decoder.accept(event(content)) + decoder.finish()
        assertEquals("beforebetweenafter", chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        assertEquals("get_time_info", chunks.filterIsInstance<StreamChunk.ToolCallStart>().single().toolName)
        assertTrue(decoder.finish().isEmpty())
    }

    @Test
    fun rejectsBlockedFirstCallWithSurroundingProseAndNeverEmitsATool() {
        val decoder = DeepSeekWebSSE("blocked-prefix", "deepseek-web")
        val chunks = decoder.accept(event("before```json\n{\"tool\":\"calendar_create\",\"arguments\":{}}\n```after"))
        assertEquals("before", chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
        val finished = decoder.finish()
        assertEquals("calendar_create", decoder.refusal!!.blockedToolName)
        assertTrue((chunks + finished).none { it is StreamChunk.ToolCallStart })
        assertEquals("beforeafter", (chunks + finished).filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
    }

    @Test
    fun disabledButAllowlistedToolsCannotExecute() {
        val decoder = DeepSeekWebSSE("disabled", "deepseek-web", availableToolNames = emptySet())
        decoder.accept(event("""{"tool":"calendar_query","arguments":{}}"""))
        assertTrue(decoder.finish().none { it is StreamChunk.ToolCallStart })
        assertEquals("calendar_query", decoder.refusal!!.blockedToolName)
    }

    @Test
    fun passesMalformedAndUnknownCallsToApplicationFallbackWithoutExecuting() {
        listOf("""before{"tool":"get_time_info","arguments":{}""",
            """before{"tool":"get_weather","arguments":{}}after""").forEach { content ->
            val decoder = DeepSeekWebSSE("fallback", "deepseek-web")
            val chunks = content.map { decoder.accept(event(it.toString())) }.flatten() + decoder.finish()
            assertEquals(content, chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
            assertTrue(chunks.none { it is StreamChunk.ToolCallStart })
        }
    }

    @Test
    fun ordinaryJsonAndCodeStillFinishWithoutDuplicatedOrMissingText() {
        listOf("before{\"name\":\"Alice\"}after", "before```html\n<p>Hello</p>\n```after", "a``b", "a<").forEach { content ->
            val decoder = DeepSeekWebSSE("ordinary", "deepseek-web")
            val chunks = content.map { decoder.accept(event(it.toString())) }.flatten() + decoder.finish()
            assertEquals(content, chunks.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text })
            assertTrue(chunks.none { it is StreamChunk.ToolCallStart })
        }
    }

    @Test
    fun decodedReadOnlyCallReachesToolExecutionAndResultIsReturnedInNextPrompt() = runBlocking {
        var executed = 0
        val definition = Tool("workspace_read_file", "test", execute = { arguments ->
            assertEquals("""{"path":"/workspace/a.txt"}""", arguments.toString())
            executed++
            listOf(UIMessagePart.Text("file-data"))
        })
        val model = DeepSeekWebModels.defaults().first()
        val handler = StreamChunkHandler(model)
        val decoder = DeepSeekWebSSE("read", "deepseek-web", availableToolNames = setOf(definition.name))
        val chunks = decoder.accept(event("before{\"tool\":\"workspace_read_file\",\"arguments\":{\"path\":\"/workspace/a.txt\"}}after")) + decoder.finish()
        var messages = listOf(UIMessage.user("Read /workspace/a.txt"))
        chunks.forEach { messages = handler.handle(messages, it) }
        val tool = messages.last().getTools().single()
        val executedTool = tool.copy(output = definition.execute(tool.inputAsJson()))
        messages = messages.dropLast(1) + messages.last().copy(parts = messages.last().parts.map {
            if (it is UIMessagePart.Tool) executedTool else it
        })
        assertEquals(1, executed)
        val prompt = DeepSeekWebTools.prompt(messages, listOf(definition), model)
        assertTrue(prompt.contains("工具返回结果：\nfile-data"))
    }

    private fun event(text: String) = buildJsonObject {
        put("p", "response/content")
        put("v", text)
    }.toString()
}
