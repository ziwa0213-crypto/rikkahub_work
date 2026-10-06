package me.rerere.ai.provider.providers.deepseekweb

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebToolsTest {
    @Test
    fun exposesOnlyReadOnlyTools() {
        assertEquals(
            setOf(
                "calendar_query",
                "get_time_info",
                "get_screen_time",
                "recent_chats",
                "conversation_search",
                "search_web",
                "scrape_web",
                "workspace_read_file",
                "ask_user",
                "text_to_speech",
                "clipboard_tool",
            ),
            DeepSeekWebTools.allowedNames,
        )
        assertTrue("calendar_create" !in DeepSeekWebTools.allowedNames)
        assertTrue("calendar_delete" !in DeepSeekWebTools.allowedNames)
        assertTrue("memory_tool" !in DeepSeekWebTools.allowedNames)
        assertTrue(DeepSeekWebTools.policy.contains("不能新建或删除日历事件"))
        assertTrue(DeepSeekWebTools.policy.contains("不能修改记忆"))
        assertTrue(DeepSeekWebTools.policy.contains("不能写入剪贴板"))
    }

    @Test
    fun allowsReadOnlyCallsAndRejectsWrites() {
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"calendar_query","arguments":{}}""").result is DeepSeekWebToolProtocol.Result.Allowed)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"workspace_read_file","arguments":{"path":"a.txt"}}""").result is DeepSeekWebToolProtocol.Result.Allowed)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"clipboard_tool","arguments":{"action":"read"}}""").result is DeepSeekWebToolProtocol.Result.Allowed)

        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"calendar_create","arguments":{}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"calendar_delete","arguments":{}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"memory_tool","arguments":{"action":"create"}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"clipboard_tool","arguments":{"action":"write"}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
    }

    @Test
    fun rejectsNonObjectArgumentsWithoutJsonTypeException() {
        val result = DeepSeekWebToolProtocol.parse("""{"tool":"calendar_query","arguments":[]}""").result
        assertTrue(result is DeepSeekWebToolProtocol.Result.Invalid)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":"clipboard_tool","arguments":{"action":[]}}""").result is DeepSeekWebToolProtocol.Result.Blocked)
        assertTrue(DeepSeekWebToolProtocol.parse("""{"tool":[],"arguments":{}}""").result is DeepSeekWebToolProtocol.Result.Invalid)
    }

    @Test
    fun suppliesExplicitFormatExamplesBeforeSchemasWithoutChangingReadonlyPolicy() {
        val prompt = DeepSeekWebTools.prompt(emptyList(), listOf(tool("get_time_info"), tool("calendar_query")), model)
        assertTrue(prompt.indexOf("调用格式（严格照此输出") < prompt.indexOf("可用工具："))
        assertTrue(prompt.contains("不要加代码围栏"))
        assertTrue(prompt.contains("不要在调用前后添加解释性文字"))
        assertTrue(prompt.contains("""{"tool": "get_time_info", "arguments": {}}"""))
        assertTrue(prompt.contains("""{"range": "today"}"""))
        assertTrue(prompt.contains("""{"range": "week", "limit": 5}"""))
        assertEquals(2, Regex(Regex.escape(DeepSeekWebTools.policy.trimIndent())).findAll(prompt).count())
        val noTools = DeepSeekWebTools.prompt(emptyList(), listOf(tool("workspace_shell")), model)
        assertFalse(noTools.contains("调用格式（严格照此输出"))
        assertFalse(noTools.contains("- workspace_shell:"))
    }

    @Test
    fun returnsReadOnlyToolResultsButNotBlockedOutputOrInvocationJson() {
        val prompt = DeepSeekWebTools.prompt(listOf(UIMessage.assistant("").copy(parts = listOf(
            UIMessagePart.Tool("read", "workspace_read_file", """{"path":"/workspace/a.txt"}""", listOf(UIMessagePart.Text("read-result"))),
            UIMessagePart.Tool("write", "workspace_write_file", "{}", listOf(UIMessagePart.Text("blocked-output"))),
            UIMessagePart.Tool("clipboard", "clipboard_tool", """{"action":"write"}""", listOf(UIMessagePart.Text("clipboard-write-output"))),
        ))), emptyList(), model)
        assertTrue(prompt.contains("工具返回结果：\nread-result"))
        assertFalse(prompt.contains("blocked-output"))
        assertFalse(prompt.contains("clipboard-write-output"))
        assertFalse(prompt.contains("\"path\""))
    }

    private val model = DeepSeekWebModels.defaults().first()
    private fun tool(name: String) = Tool(name, "test", execute = { emptyList() })
}
