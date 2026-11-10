package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolPolicy
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.service.pulse.PulseToolContext
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PulseToolPolicyTest {
    @Test fun `alarm is opt in independent of information and uses scheduled receipt guard`() = runBlocking {
        val alarm = tool("set_alarm")
        assertTrue(applyPulseToolPolicy(listOf(alarm), PulseConfig(enableInformation = false)).isEmpty())
        assertTrue(applyPulseToolPolicy(listOf(alarm), PulseConfig()).isEmpty())
        val offered = applyPulseToolPolicy(listOf(alarm), PulseConfig(enableInformation = false, enableAlarm = true))
        assertEquals(listOf("set_alarm"), offered.map { it.name })
        val intercepted = mutableListOf<String>()
        withContext(PulseToolContext { name, _, block -> intercepted += name; block() }) {
            offered.single().execute(buildJsonObject {})
        }
        assertEquals(listOf("set_alarm"), intercepted)
        val providerAllowed = listOf(alarm).filter { it.name in DeepSeekWebToolPolicy.baseAllowedNames }
        assertTrue(applyPulseToolPolicy(providerAllowed, PulseConfig(enableAlarm = true)).isEmpty())
    }
    @Test fun sensingSwitchesAreIndependentAndProviderRestrictionsStillWin() = runBlocking {
        val names = listOf("read_notifications", "read_sms", "send_sms")
        assertTrue(applyPulseToolPolicy(names.map { tool(it) }, PulseConfig()).isEmpty())
        val config = PulseConfig(enableInformation = false, enableNotificationReading = true, enableSmsReading = true, enableSmsSending = true)
        assertEquals(names, applyPulseToolPolicy(names.map { tool(it) }, config).map { it.name })
        assertTrue(applyPulseToolPolicy(names.filter { it in DeepSeekWebToolPolicy.baseAllowedNames }.map { tool(it) }, config).isEmpty())
        val intercepted = mutableListOf<String>()
        withContext(PulseToolContext { name, _, block -> intercepted += name; block() }) {
            applyPulseToolPolicy(names.map { tool(it) }, config).forEach { it.execute(buildJsonObject {}) }
        }
        assertEquals(listOf("send_sms"), intercepted)
    }
    private fun tool(name: String, action: () -> Unit = {}) = Tool(name = name, description = name,
        execute = { action(); emptyList() })

    @Test fun `interactive and destructive capabilities stay unavailable`() {
        val excluded = listOf("ask_user", "text_to_speech", "eval_javascript", "calendar_delete", "memory_delete",
            "workspace_shell", "workspace_write_file", "use_skill", "sessions_spawn", "browser_open", "mcp__a__b")
        assertTrue(applyPulseToolPolicy(excluded.map { tool(it) }, PulseConfig()).isEmpty())
    }
    @Test fun `disabled information and memory groups expose no tools`() {
        val tools = listOf("calendar_query", "get_time_info", "clipboard_tool", "memory_tool", "memory_write", "calendar_create")
        assertTrue(applyPulseToolPolicy(tools.map { tool(it) }, PulseConfig(enableInformation = false,
            enableMemory = false, enableCalendarCreate = false)).isEmpty())
    }
    @Test fun `clipboard write and row deletion are blocked before side effect`() = runBlocking {
        var writes = 0
        val tools = applyPulseToolPolicy(listOf(tool("clipboard_tool") { writes++ }, tool("memory_tool") { writes++ }), PulseConfig())
        assertTrue(runCatching { tools[0].execute(buildJsonObject { put("action", "write") }) }.isFailure)
        assertTrue(runCatching { tools[1].execute(buildJsonObject { put("action", "delete") }) }.isFailure)
        assertTrue(runCatching { tools[1].execute(buildJsonObject {}) }.isFailure)
        assertEquals(0, writes)
        tools[0].execute(buildJsonObject { put("action", "read") })
        tools[1].execute(buildJsonObject { put("action", "create") })
        assertEquals(2, writes)
    }
    @Test fun `pulse filtering never restores a provider restricted tool`() {
        val offered = listOf("calendar_create", "calendar_query", "memory_tool", "memory_read", "schedule_task")
            .filter { it in DeepSeekWebToolPolicy.baseAllowedNames }.map { tool(it) }
        assertEquals(setOf("calendar_query", "memory_read"), applyPulseToolPolicy(offered,
            PulseConfig(enableScheduledTasks = true)).map { it.name }.toSet())
    }
    @Test fun `only scheduled writes enter the durable receipt guard`() = runBlocking {
        val intercepted = mutableListOf<String>()
        val context = PulseToolContext { name, _, block -> intercepted += name; block() }
        val tools = applyPulseToolPolicy(listOf(tool("calendar_create"), tool("calendar_query")), PulseConfig())
        withContext(context) { tools.forEach { it.execute(buildJsonObject {}) } }
        assertEquals(listOf("calendar_create"), intercepted)
    }
    @Test fun `schedule tool is opt in`() {
        assertTrue(applyPulseToolPolicy(listOf(tool("schedule_task")), PulseConfig()).isEmpty())
        assertEquals(1, applyPulseToolPolicy(listOf(tool("schedule_task")), PulseConfig(enableScheduledTasks = true)).size)
    }
}
