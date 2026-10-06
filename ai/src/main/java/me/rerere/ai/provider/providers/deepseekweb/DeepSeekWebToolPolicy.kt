package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shared by wire parsing and the application's output guard. */
object DeepSeekWebToolPolicy {
    val allowedNames = setOf(
        "calendar_query", "get_time_info", "get_screen_time",
        "recent_chats", "conversation_search", "ask_user", "text_to_speech",
        "search_web", "scrape_web", "clipboard_tool", "workspace_read_file",
    )

    fun allows(name: String, arguments: JsonObject): Boolean =
        name in allowedNames &&
            (name != "clipboard_tool" ||
                (arguments["action"] as? JsonPrimitive)?.contentOrNull == "read")

    fun isBlockedName(name: String): Boolean = name in setOf(
        "calendar_create", "calendar_delete", "memory_tool", "workspace_write_file", "workspace_edit_file",
        "workspace_shell", "eval_javascript", "use_skill",
    ) || name.startsWith("mcp__", ignoreCase = true)
}
