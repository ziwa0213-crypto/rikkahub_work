package me.rerere.rikkahub.browser

import me.rerere.rikkahub.R

enum class BrowserToolCategory {
    READ,
    WRITE,
    LOOP,
}

data class BrowserToolDefinition(
    val name: String,
    val category: BrowserToolCategory,
    val titleRes: Int,
    val descriptionRes: Int,
    val enabledByDefault: Boolean = true,
)

/** Single source for the built-in browser tool names, display order, categories and defaults. */
object BrowserToolRegistry {
    val tools = listOf(
        BrowserToolDefinition("browser_open", BrowserToolCategory.READ, R.string.browser_tool_open_title, R.string.browser_tool_open_desc),
        BrowserToolDefinition("browser_state", BrowserToolCategory.READ, R.string.browser_tool_state_title, R.string.browser_tool_state_desc),
        BrowserToolDefinition("browser_read", BrowserToolCategory.READ, R.string.browser_tool_read_title, R.string.browser_tool_read_desc),
        BrowserToolDefinition("browser_list_actions", BrowserToolCategory.READ, R.string.browser_tool_actions_title, R.string.browser_tool_actions_desc),
        BrowserToolDefinition("browser_click", BrowserToolCategory.WRITE, R.string.browser_tool_click_title, R.string.browser_tool_click_desc),
        BrowserToolDefinition("browser_type", BrowserToolCategory.WRITE, R.string.browser_tool_type_title, R.string.browser_tool_type_desc),
        BrowserToolDefinition("browser_scroll", BrowserToolCategory.WRITE, R.string.browser_tool_scroll_title, R.string.browser_tool_scroll_desc),
        BrowserToolDefinition("browser_done", BrowserToolCategory.LOOP, R.string.browser_tool_done_title, R.string.browser_tool_done_desc),
    )

    fun isEnabled(disabledNames: Collection<String>, name: String): Boolean =
        tools.firstOrNull { it.name == name }?.let { it.enabledByDefault && name !in disabledNames } ?: false

    fun forCategory(category: BrowserToolCategory): List<BrowserToolDefinition> =
        tools.filter { it.category == category }
}
