package me.rerere.ai.provider

import kotlinx.serialization.Serializable

/** Per-provider opt-in for DeepSeek Web tools beyond its read-only baseline. */
@Serializable
data class DeepSeekWebUnlock(
    val customEnabled: Boolean = false,
    val calendarCreate: Boolean = false,
    val calendarDelete: Boolean = false,
    val memoryWrite: Boolean = false,
    val workspaceWrite: Boolean = false,
    val workspaceShell: Boolean = false,
    val evalJavascript: Boolean = false,
    val useSkill: Boolean = false,
    val mcp: Boolean = false,
    val clipboardWrite: Boolean = false,
) {
    val active: DeepSeekWebUnlock
        get() = if (customEnabled) this else DISABLED

    companion object {
        val DISABLED = DeepSeekWebUnlock()
    }
}
