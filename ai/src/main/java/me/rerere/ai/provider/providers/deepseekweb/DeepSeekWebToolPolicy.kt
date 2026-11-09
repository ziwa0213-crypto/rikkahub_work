package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.provider.DeepSeekWebUnlock

/** Shared by wire parsing, the prompt builder and the application's output guard. */
object DeepSeekWebToolPolicy {
    val baseAllowedNames = setOf(
        "calendar_query", "get_time_info", "get_screen_time",
        "recent_chats", "conversation_search", "ask_user", "text_to_speech",
        "search_web", "scrape_web", "clipboard_tool", "workspace_read_file", "memory_read", "memory_list",
    )

    private val memoryWriteNames = setOf(
        "memory_tool", "memory_write", "memory_append", "memory_str_replace", "memory_delete",
    )
    private val workspaceWriteNames = setOf("workspace_write_file", "workspace_edit_file")
    private val blockedNames = setOf(
        "calendar_create", "calendar_delete", "workspace_write_file", "workspace_edit_file",
        "workspace_shell", "eval_javascript", "use_skill",
    ) + memoryWriteNames

    fun allowedNames(unlock: DeepSeekWebUnlock = DeepSeekWebUnlock.DISABLED): Set<String> {
        val active = unlock.active
        if (!active.customEnabled) return baseAllowedNames
        return buildSet {
            addAll(baseAllowedNames)
            if (active.calendarCreate) add("calendar_create")
            if (active.calendarDelete) add("calendar_delete")
            if (active.memoryWrite) addAll(memoryWriteNames)
            if (active.workspaceWrite) addAll(workspaceWriteNames)
            if (active.workspaceShell) add("workspace_shell")
            if (active.evalJavascript) add("eval_javascript")
            if (active.useSkill) add("use_skill")
        }
    }

    fun isUnlocked(name: String, unlock: DeepSeekWebUnlock = DeepSeekWebUnlock.DISABLED): Boolean {
        val active = unlock.active
        if (!active.customEnabled) return false
        return when {
            name.startsWith("mcp__", ignoreCase = true) -> active.mcp
            name == "calendar_create" -> active.calendarCreate
            name == "calendar_delete" -> active.calendarDelete
            name in memoryWriteNames -> active.memoryWrite
            name in workspaceWriteNames -> active.workspaceWrite
            name == "workspace_shell" -> active.workspaceShell
            name == "eval_javascript" -> active.evalJavascript
            name == "use_skill" -> active.useSkill
            name == "clipboard_tool" -> active.clipboardWrite
            else -> false
        }
    }

    fun allows(
        name: String,
        arguments: JsonObject,
        unlock: DeepSeekWebUnlock = DeepSeekWebUnlock.DISABLED,
    ): Boolean {
        val active = unlock.active
        val mcpAllowed = active.customEnabled && active.mcp && name.startsWith("mcp__", ignoreCase = true)
        if (name !in allowedNames(active) && !mcpAllowed) return false
        if (name == "clipboard_tool") {
            val action = (arguments["action"] as? JsonPrimitive)?.contentOrNull
            return action == "read" || (active.customEnabled && active.clipboardWrite && action == "write")
        }
        return true
    }

    fun isBlockedName(name: String, unlock: DeepSeekWebUnlock = DeepSeekWebUnlock.DISABLED): Boolean {
        val blockable = name in blockedNames || name.startsWith("mcp__", ignoreCase = true)
        return blockable && !isUnlocked(name, unlock)
    }
}
