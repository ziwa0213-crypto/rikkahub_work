package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.ToolApprovalMode

enum class ToolTier {
    L0_READ,
    L1_SELF,
    L2_CREATE,
    L3_REWRITE,
}

private const val MCP_PREFIX = "mcp__"
private val DEFAULT_TIER = ToolTier.L3_REWRITE

// HITL tools must be intercepted by the approval flow because their execute path is not callable directly.
private val HITL_TOOLS = setOf("ask_user")

private val TIERS: Map<String, (JsonElement) -> ToolTier> = mapOf(
    "calendar_query" to { _ -> ToolTier.L0_READ },
    "chart_display" to { _ -> ToolTier.L0_READ },
    "get_time_info" to { _ -> ToolTier.L0_READ },
    "get_screen_time" to { _ -> ToolTier.L0_READ },
    "recent_chats" to { _ -> ToolTier.L0_READ },
    "conversation_search" to { _ -> ToolTier.L0_READ },
    "search_web" to { _ -> ToolTier.L0_READ },
    "scrape_web" to { _ -> ToolTier.L0_READ },
    "use_skill" to { _ -> ToolTier.L0_READ },
    "text_to_speech" to { _ -> ToolTier.L1_SELF },
    "ask_user" to { _ -> ToolTier.L1_SELF },
    "calendar_create" to { _ -> ToolTier.L2_CREATE },
    "calendar_delete" to { _ -> ToolTier.L3_REWRITE },
    "eval_javascript" to { _ -> ToolTier.L3_REWRITE },
    "workspace_read_file" to { _ -> ToolTier.L3_REWRITE },
    "workspace_write_file" to { _ -> ToolTier.L3_REWRITE },
    "workspace_edit_file" to { _ -> ToolTier.L3_REWRITE },
    "workspace_shell" to { _ -> ToolTier.L3_REWRITE },
    "clipboard_tool" to { args ->
        if (args.jsonObject["action"]?.jsonPrimitive?.contentOrNull == "read") {
            ToolTier.L0_READ
        } else {
            ToolTier.L3_REWRITE
        }
    },
    "memory_tool" to { args ->
        when (args.jsonObject["action"]?.jsonPrimitive?.contentOrNull) {
            "create" -> ToolTier.L2_CREATE
            "edit", "delete" -> ToolTier.L3_REWRITE
            else -> ToolTier.L3_REWRITE
        }
    },
)

fun tierOf(toolName: String, args: JsonElement): ToolTier = when {
    toolName.startsWith(MCP_PREFIX) -> ToolTier.L3_REWRITE
    else -> TIERS[toolName]?.invoke(args) ?: DEFAULT_TIER
}

fun requiresApprovalInPartial(toolName: String, args: JsonElement): Boolean =
    tierOf(toolName, args) >= ToolTier.L3_REWRITE

fun applyApprovalMode(tools: List<Tool>, mode: ToolApprovalMode): List<Tool> =
    tools.map { tool ->
        tool.copy(
            needsApproval = { args ->
                if (tool.name in HITL_TOOLS) {
                    true
                } else {
                    when (mode) {
                        ToolApprovalMode.AskAll -> true
                        ToolApprovalMode.Partial ->
                            tool.needsApproval(args) || requiresApprovalInPartial(tool.name, args)

                        ToolApprovalMode.AllowAll -> false
                    }
                }
            }
        )
    }
