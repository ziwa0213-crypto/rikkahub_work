package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepSeekWebOutputGuardTest {
    @Test
    fun stripsFencedBlockedToolJsonAndReportsToolName() {
        val result = DeepSeekWebOutputGuard.sanitize(
            listOf(UIMessage.assistant("结果如下：\n```json\n{\"tool\":\"workspace_shell\",\"arguments\":{}}\n```"))
        )

        assertNotNull(result)
        assertEquals("workspace_shell", result!!.matches.single().toolName)
        assertEquals("结果如下：", result.messages.single().toText().trim())
    }

    @Test
    fun stripsBareAlternateToolJsonAndClipboardWrite() {
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(
            "{\"name\":\"calendar_delete\",\"parameters\":{\"id\":\"1\"}}\n" +
                "{\"tool\":\"clipboard_tool\",\"arguments\":{\"action\":\"write\"}}"
        )))

        assertNotNull(result)
        assertEquals(
            setOf("calendar_delete", "clipboard_tool"),
            result!!.matches.map { it.toolName }.toSet(),
        )
        assertEquals(listOf(""), result.messages.map { it.toText() })
    }

    @Test
    fun keepsReadOnlyCallsAndOrdinaryJsonText() {
        assertNull(
            DeepSeekWebOutputGuard.sanitize(
                listOf(UIMessage.assistant("{\"tool\":\"clipboard_tool\",\"arguments\":{\"action\":\"read\"}}"))
            )
        )
        assertNull(
            DeepSeekWebOutputGuard.sanitize(
                listOf(UIMessage.assistant("示例：{\"name\":\"Alice\",\"age\":30}"))
            )
        )
    }

    @Test
    fun stripsForbiddenDsmlAndKeepsSurroundingText() {
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(
            "before" + dsml("use_skill") + "after"
        )))!!
        assertEquals("beforeafter", result.messages.single().toText())
        assertEquals("use_skill", result.matches.single().toolName)
        assertTrue(result.matches.single().blocked)
    }

    @Test
    fun reportsAllowedDsmlWithoutExecutingOrRemovingIt() {
        val content = dsml("ask_user", """<||DSML||parameter name="questions" string="false">[{"id":"intent","question":"What next?"}]</||DSML||parameter>""")
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
        assertEquals(content, result.messages.single().toText())
        assertEquals("ask_user", result.matches.single().toolName)
        assertFalse(result.matches.single().blocked)
    }

    @Test
    fun stripsOnlyForbiddenInvocationFromMixedDsmlBlock() {
        val blocked = """<||DSML||invoke name="use_skill"></||DSML||invoke>"""
        val allowed = """<||DSML||invoke name="ask_user"></||DSML||invoke>"""
        val content = "before<||DSML||calls>$blocked$allowed</||DSML||calls>after"
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
        assertEquals("before<||DSML||calls>$allowed</||DSML||calls>after", result.messages.single().toText())
        assertEquals(listOf("use_skill", "ask_user"), result.matches.map { it.toolName })
        assertEquals(listOf(true, false), result.matches.map { it.blocked })
    }

    @Test
    fun classifiesJsonClipboardActionInDsmlWithoutUnsafeCasts() {
        listOf("\"read\"" to false, "\"write\"" to true, "[]" to true).forEach { (action, blocked) ->
            val content = dsml("clipboard_tool", """<||DSML||parameter name="action" string="false">$action</||DSML||parameter>""")
            val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
            assertEquals(blocked, result.matches.single().blocked)
            assertEquals(if (blocked) "" else content, result.messages.single().toText())
        }
    }

    @Test
    fun detectsDsmlAcrossTextPartsAndRestrictsClipboardAction() {
        listOf("read" to false, "write" to true).forEach { (action, blocked) ->
            val content = dsml("clipboard_tool", """<||DSML||parameter name="action" string="true">$action</||DSML||parameter>""")
            val message = UIMessage.assistant("").copy(parts = content.chunked(7).map { UIMessagePart.Text(it) })
            val result = DeepSeekWebOutputGuard.sanitize(listOf(message))!!
            assertEquals(blocked, result.matches.single().blocked)
            assertEquals(if (blocked) "" else content, result.messages.single().toText())
        }
    }

    @Test
    fun blocksUnknownDsmlAndUnwrappedInvoke() {
        val content = """<||DSML||invoke name="mcp__server_delete"></||DSML||invoke>"""
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
        assertTrue(result.matches.single().blocked)
        assertEquals("", result.messages.single().toText())
    }

    @Test
    fun ignoresHistoricalAssistantAndUserToolExamples() {
        assertNull(DeepSeekWebOutputGuard.sanitize(listOf(
            UIMessage.assistant(dsml("use_skill")),
            UIMessage.user("What is DSML?"),
            UIMessage.assistant("A tool markup format."),
        )))
        assertNull(DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.user(dsml("use_skill")))))
    }

    @Test
    fun malformedToolJsonProducesFormatFeedbackWithoutDeletingContent() {
        val content = "before{\"tool\":\"calendar_query\",\"arguments\":{},bad}after"
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
        assertEquals(content, result.messages.single().toText())
        assertFalse(result.matches.single().blocked)
        assertEquals("JSON", result.matches.single().toolName)
    }

    @Test
    fun unknownExampleIsPreservedAndReportedRatherThanExecutedOrStripped() {
        val content = "Example: ```json\n{\"tool\":\"get_weather\",\"arguments\":{}}\n```"
        val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
        assertEquals(content, result.messages.single().toText())
        assertEquals("get_weather", result.matches.single().toolName)
        assertFalse(result.matches.single().blocked)
    }

    @Test
    fun missingToolNameAndInvalidArgumentsAreReportedWithoutJsonTypeCrashes() {
        listOf("{\"arguments\":{}}", "{\"tool\":\"calendar_query\",\"arguments\":[]}").forEach { content ->
            val result = DeepSeekWebOutputGuard.sanitize(listOf(UIMessage.assistant(content)))!!
            assertEquals(content, result.messages.single().toText())
            assertFalse(result.matches.single().blocked)
        }
    }

    private fun dsml(name: String, parameters: String = ""): String =
        """<||DSML||calls><||DSML||invoke name="$name">$parameters</||DSML||invoke></||DSML||calls>"""
}
