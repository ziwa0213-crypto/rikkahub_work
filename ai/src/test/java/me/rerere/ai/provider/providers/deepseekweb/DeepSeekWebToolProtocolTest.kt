package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebToolProtocolTest {
    private val call = """{"tool":"workspace_read_file","arguments":{"path":"/workspace/a.txt"}}"""

    @Test
    fun parsesBareAndFencedJsonIncludingNestedArguments() {
        for (input in listOf(call, "```json\n$call\n```", "```\n$call\n```", "```JSON\n$call\n```")) {
            val result = DeepSeekWebToolProtocol.parse(input)
            assertTrue(result.result is DeepSeekWebToolProtocol.Result.Allowed)
            assertTrue(result.text.isBlank())
            val parsed = (result.result as DeepSeekWebToolProtocol.Result.Allowed).call
            assertEquals("workspace_read_file", parsed.name)
            assertEquals("""{"path":"/workspace/a.txt"}""", parsed.arguments)
        }
    }

    @Test
    fun keepsProseAroundBareAndFencedCalls() {
        for (input in listOf("before${call}after", "before```json\n${call}\n```after")) {
            val result = DeepSeekWebToolProtocol.parse(input)
            assertEquals("beforeafter", result.text)
            assertTrue(result.result is DeepSeekWebToolProtocol.Result.Allowed)
        }
    }

    @Test
    fun onlyFirstCallIsEligibleAndOtherCallsAreRemoved() {
        val result = DeepSeekWebToolProtocol.parse("a${call}b{\"tool\":\"calendar_delete\",\"arguments\":{}}c")
        assertTrue(result.result is DeepSeekWebToolProtocol.Result.Allowed)
        assertEquals("abc", result.text)
        val blockedFirst = DeepSeekWebToolProtocol.parse("{\"tool\":\"calendar_delete\",\"arguments\":{}}$call")
        assertTrue(blockedFirst.result is DeepSeekWebToolProtocol.Result.Blocked)
        assertTrue(blockedFirst.text.isBlank())
    }

    @Test
    fun firstCallCanBeFoundAfterOrdinaryJson() {
        val ordinary = """{"name":"Alice","age":30}"""
        val result = DeepSeekWebToolProtocol.parse("$ordinary$call")
        assertTrue(result.result is DeepSeekWebToolProtocol.Result.Allowed)
        assertEquals(ordinary, result.text)
    }

    @Test
    fun matchesBracesAndEscapesInsideStringsWithoutSplittingArguments() {
        val path = "/workspace/{a}\"\\b.txt"
        val arguments = JsonObject(mapOf("path" to JsonPrimitive(path)))
        val encoded = JsonObject(mapOf("tool" to JsonPrimitive("workspace_read_file"), "arguments" to arguments)).toString()
        val result = DeepSeekWebToolProtocol.parse("before${encoded}after")
        assertEquals(arguments.toString(), (result.result as DeepSeekWebToolProtocol.Result.Allowed).call.arguments)
        assertEquals("beforeafter", result.text)
    }

    @Test
    fun rejectsAllStateChangingNamesAndMcpPrefix() {
        listOf("calendar_create", "calendar_delete", "memory_tool", "workspace_write_file", "workspace_edit_file",
            "workspace_shell", "eval_javascript", "use_skill", "mcp__server_tool").forEach { name ->
            val result = DeepSeekWebToolProtocol.parse("""{"tool":"$name","arguments":{}}""")
            assertEquals(DeepSeekWebToolProtocol.Result.Blocked(name), result.result)
            assertTrue(result.text.isBlank())
        }
    }

    @Test
    fun unknownToolsKeepOriginalTextWithoutExecuting() {
        val input = """Example: {"tool":"get_weather","arguments":{"city":"Paris"}}"""
        val result = DeepSeekWebToolProtocol.parse(input)
        assertEquals(DeepSeekWebToolProtocol.Result.Unknown("get_weather"), result.result)
        assertEquals(input, result.text)
    }

    @Test
    fun reportsMalformedOrMissingFieldsWithoutThrowingOrRemovingText() {
        listOf(
            """{"tool":"get_time_info","arguments":{}""",
            """{"tool":"get_time_info","arguments":{},broken}""",
            """{"tool":"get_time_info","arguments":[]}""",
            """{"arguments":{}}""", """{"tool":"get_time_info"}""",
            """{"tool":[],"arguments":{}}""", """{"tool":false,"arguments":{}}""",
        ).forEach { input ->
            val result = DeepSeekWebToolProtocol.parse(input)
            assertTrue(result.result is DeepSeekWebToolProtocol.Result.Invalid)
            assertEquals(input, result.text)
        }
    }

    @Test
    fun supportsPreviouslyAcceptedArgumentAliases() {
        for (input in listOf("""{"name":"get_time_info","parameters":{}}""",
            """{"tool":"get_time_info","input":{}}""")) {
            assertTrue(DeepSeekWebToolProtocol.parse(input).result is DeepSeekWebToolProtocol.Result.Allowed)
        }
    }

    @Test
    fun doesNotTreatDsmlParametersAsJsonCalls() {
        val input = """<||DSML||invoke name="ask_user"><||DSML||parameter name="questions" string="false">[$call]</||DSML||parameter></||DSML||invoke>"""
        val result = DeepSeekWebToolProtocol.parse(input)
        assertEquals(DeepSeekWebToolProtocol.Result.NotTool, result.result)
        assertEquals(input, result.text)
    }

    @Test
    fun leavesChatCodeAndOrdinaryJsonAlone() {
        listOf("你好", "怎么执行 HTML？", "```html\n<html>Hello</html>\n```", "2 < 3",
            "{\"name\":\"Alice\",\"age\":30}", "function f() { return 1; }").forEach { input ->
            val result = DeepSeekWebToolProtocol.parse(input)
            assertEquals(DeepSeekWebToolProtocol.Result.NotTool, result.result)
            assertEquals(input, result.text)
        }
    }

    @Test
    fun clipboardRemainsReadOnly() {
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"clipboard_tool","arguments":{"action":"read"}}""").result is DeepSeekWebToolProtocol.Result.Allowed)
        for (action in listOf("\"write\"", "[]", "null", "false")) {
            assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"clipboard_tool","arguments":{"action":$action}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
        }
        assertFalse(DeepSeekWebToolPolicy.isBlockedName("get_weather"))
    }
}
