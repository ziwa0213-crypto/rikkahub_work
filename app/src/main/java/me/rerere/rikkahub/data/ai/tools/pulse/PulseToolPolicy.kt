package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.currentCoroutineContext
import me.rerere.rikkahub.service.pulse.PulseToolContext
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.ai.tools.MemoryToolNames
import me.rerere.rikkahub.data.datastore.PulseConfig

/** Provider restrictions run first. Pulse can never restore a provider-blocked tool. */
internal fun applyPulseToolPolicy(tools: List<Tool>, pulse: PulseConfig): List<Tool> =
    tools.filter { tool ->
        when (tool.name) {
            "get_time_info", "get_screen_time", "search_web", "scrape_web", "clipboard_tool",
            "calendar_query", "recent_chats", "conversation_search", "workspace_read_file" -> pulse.enableInformation
            "set_alarm" -> pulse.enableAlarm
            "workspace_write_file", "workspace_edit_file" -> pulse.enableFileWrite
            "workspace_shell" -> pulse.enableShell
            "memory_tool", MemoryToolNames.READ, MemoryToolNames.LIST, MemoryToolNames.WRITE,
            MemoryToolNames.STR_REPLACE, MemoryToolNames.APPEND -> pulse.enableMemory
            "calendar_create" -> pulse.enableCalendarCreate
            "schedule_task" -> pulse.enableScheduledTasks
            "update_heartbeat_prompt" -> pulse.enableSelfPromptEdit
            "read_notifications" -> pulse.enableNotificationReading
            "read_sms" -> pulse.enableSmsReading
            "send_sms" -> pulse.enableSmsSending
            else -> false
        }
    }.map { tool ->
        val actions = when (tool.name) {
            "clipboard_tool" -> setOf("read")
            "memory_tool" -> setOf("create", "edit")
            "update_heartbeat_prompt" -> setOf("read", "edit") + if (pulse.enableSelfPromptRewrite) setOf("rewrite") else emptySet()
            else -> null
        }
        val execute = tool.execute
        val writes = tool.name in (MemoryToolNames.WRITES + setOf(
            "memory_tool", "calendar_create", "schedule_task", "update_heartbeat_prompt", "workspace_write_file", "workspace_edit_file", "workspace_shell", "send_sms", "set_alarm",
        ))
        tool.copy(
                description = tool.description + if (actions == null) "" else "\nOnly these actions are available: ${actions.joinToString()}.",
                execute = { args ->
                    val action = ((args as? JsonObject)?.get("action") as? JsonPrimitive)?.content
                        ?: if (tool.name == "update_heartbeat_prompt") "edit" else null
                    require(actions == null || action in actions) { "Action is unavailable in Pulse mode." }
                    val guard = currentCoroutineContext()[PulseToolContext]
                    val readOnly = (tool.name == "schedule_task" && action == "list") ||
                        (tool.name == "update_heartbeat_prompt" && action == "read")
                    if (writes && !readOnly && guard != null) guard.execute(tool.name, args) { execute(args) } else execute(args)
                },
            )
    }
