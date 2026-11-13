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
    @Test fun `H9 missing action defaults only for heartbeat prompt and rewrite follows both switches`() = runBlocking {
        var edits = 0
        val all = listOf(tool("update_heartbeat_prompt") { edits++ }, tool("clipboard_tool") { edits++ }, tool("memory_tool") { edits++ })
        val offered = applyPulseToolPolicy(all, PulseConfig())
        offered.first().execute(buildJsonObject {})
        assertEquals(1, edits)
        assertTrue(runCatching { offered[1].execute(buildJsonObject {}) }.isFailure)
        assertTrue(runCatching { offered[2].execute(buildJsonObject {}) }.isFailure)
        assertTrue(runCatching { offered.first().execute(buildJsonObject { put("action", "rewrite") }) }.isFailure)
        assertEquals(1, edits)
        assertTrue(applyPulseToolPolicy(all.take(1), PulseConfig(enableSelfPromptEdit = false, enableSelfPromptRewrite = true)).isEmpty())
        applyPulseToolPolicy(all.take(1), PulseConfig(enableSelfPromptRewrite = true)).single()
            .execute(buildJsonObject { put("action", "rewrite") })
        assertEquals(2, edits)
        offered.first().execute(buildJsonObject { put("action", "read") })
        assertEquals(3, edits)
    }

    @Test fun `heartbeat reads bypass durable receipts while edit and rewrite stay guarded`() = runBlocking {
        var executions = 0
        val guarded = mutableListOf<String>()
        val tool = applyPulseToolPolicy(listOf(tool("update_heartbeat_prompt") { executions++ }),
            PulseConfig(enableSelfPromptRewrite = true)).single()
        withContext(PulseToolContext { name, _, block -> guarded += name; block() }) {
            repeat(2) { tool.execute(buildJsonObject { put("action", "read") }) }
            assertEquals(2, executions)
            assertTrue(guarded.isEmpty())
            tool.execute(buildJsonObject { put("prompt", "default edit") })
            tool.execute(buildJsonObject { put("action", "edit"); put("prompt", "edit") })
            tool.execute(buildJsonObject { put("action", "rewrite"); put("prompt", "rewrite") })
            assertTrue(runCatching { tool.execute(buildJsonObject { put("action", "unknown") }) }.isFailure)
        }
        assertEquals(5, executions)
        assertEquals(List(3) { "update_heartbeat_prompt" }, guarded)
    }

    @Test fun `task listing bypasses write receipt while mutations remain guarded`() = runBlocking {
        val guarded = mutableListOf<String>()
        val tool = applyPulseToolPolicy(listOf(tool("schedule_task")), PulseConfig(enableScheduledTasks = true)).single()
        withContext(PulseToolContext { name, _, block -> guarded += name; block() }) {
            tool.execute(buildJsonObject { put("action", "list") })
            assertTrue(guarded.isEmpty())
            tool.execute(buildJsonObject { put("action", "delete"); put("id", "x") })
            tool.execute(buildJsonObject {})
        }
        assertEquals(listOf("schedule_task", "schedule_task"), guarded)
    }
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
