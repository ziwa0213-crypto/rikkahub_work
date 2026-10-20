package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.first
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.BrowserException
import me.rerere.rikkahub.browser.CredentialFilter
import me.rerere.rikkahub.browser.BrowserToolRegistry
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import kotlin.uuid.Uuid

internal fun buildBrowserTools(
    controller: BrowserController,
    eventBus: AppEventBus,
    settingsStore: SettingsStore,
    conversationId: Uuid?,
): List<Tool> {
    val tools = listOf(
    Tool(
        name = "browser_read",
        description = "Read the current built-in browser page after local sensitive-content filtering.",
        parameters = { emptySchema() },
        execute = {
            controller.readText(conversationId, aiCall = true).toFilteredTextToolResult(settingsStore, controller) { text, hitCount ->
                buildJsonObject {
                    put("text", text)
                    put("filtered_count", hitCount)
                    if (text.length < 50) put("notice", "This page may not be readable as text. Ask the user to select text or send a screenshot manually; no screenshot was taken.")
                }
            }
        },
    ),
    Tool(
        name = "browser_list_actions",
        description = "List visible links, buttons, and form controls on the current page. Use the returned id before browser_click or browser_type.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("max", buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum number of controls to list, from 1 to 60.")
                    })
                }
            )
        },
        execute = { args ->
            val max = args.jsonObject["max"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 60
            controller.listClickables(max, conversationId, aiCall = true).toFilteredClickableToolResult(settingsStore, controller) { controls, hitCount ->
                buildJsonObject {
                    put("filtered_count", hitCount)
                    put("actions", buildJsonArray {
                        controls.forEach { control ->
                            add(buildJsonObject {
                                put("id", control.id)
                                put("tag", control.tag)
                                put("type", control.type)
                                put("text", control.text)
                            })
                        }
                    })
                }
            }
        },
    ),
    Tool(
        name = "browser_state",
        description = "Read the current built-in browser title, URL, loading state, and display mode.",
        parameters = { emptySchema() },
        execute = {
            controller.state(conversationId, aiCall = true).toSimpleToolResult { state ->
                val title = filterBrowserContent(settingsStore, state.title)
                val url = filterBrowserContent(settingsStore, state.url)
                controller.recordFilteredCount(title.hitCount + url.hitCount)
                buildJsonObject {
                    put("title", title.text)
                    put("url", url.text)
                    put("filtered_count", title.hitCount + url.hitCount)
                    put("loading", state.loading)
                    put("mode", if (state.desktopMode) "desktop" else "mobile")
                }
            }
        },
    ),
    Tool(
        name = "browser_click",
        description = "Click a visible, non-dangerous control by id from browser_list_actions. Payment, deletion, authorization, publishing, transfer, and file controls are always blocked for the human to perform.",
        parameters = { requiredSchema("id", "Action id from browser_list_actions.") },
        execute = { args -> controller.click(requiredString(args, "id"), conversationId, aiCall = true).toSimpleToolResult() },
    ),
    Tool(
        name = "browser_type",
        description = "Type into a visible, non-password form control by id from browser_list_actions. Password and file inputs are always blocked.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject { put("type", "string") })
                    put("text", buildJsonObject { put("type", "string") })
                },
                required = listOf("id", "text"),
            )
        },
        execute = { args ->
            controller.typeInto(requiredString(args, "id"), requiredString(args, "text"), conversationId, aiCall = true).toSimpleToolResult()
        },
    ),
    Tool(
        name = "browser_scroll",
        description = "Scroll the current built-in browser page by a bounded horizontal and vertical distance.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("dx", buildJsonObject { put("type", "integer") })
                    put("dy", buildJsonObject { put("type", "integer") })
                },
                required = listOf("dy"),
            )
        },
        execute = { args ->
            val dx = args.jsonObject["dx"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val dy = args.jsonObject["dy"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            controller.scrollBy(dx, dy, conversationId, aiCall = true).toSimpleToolResult()
        },
    ),
    Tool(
        name = "browser_open",
        description = "Open the built-in browser and navigate to a URL. Use this when scrape_web cannot retrieve a page because it requires login, a CAPTCHA, or JavaScript rendering. The browser runs on the device; the user signs in or passes verification themselves. Page content is filtered locally before it is sent to you. After the user confirms, call browser_read.",
        parameters = { requiredSchema("url", "An http or https URL.") },
        execute = { args ->
            val url = requiredString(args, "url")
            BrowserController.validateUrl(url)?.let { return@Tool listOf(errorText(it)) }
            val id = conversationId ?: return@Tool listOf(errorText(BrowserException("NO_CONVERSATION", "The browser needs a conversation context.")))
            controller.beginTask(id)?.let { return@Tool listOf(errorText(it)) }
            val active = controller.active
            if (active == null) {
                eventBus.emit(AppEvent.OpenBrowser(id, url))
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("status", "browser_opened")
                    put("message", "The built-in browser is opening at the URL. If login or a CAPTCHA is shown, tell the user to complete it themselves, then call browser_read only after they confirm.")
                }.toString()))
            } else if (active.conversationId != id) {
                listOf(errorText(BrowserException("BROWSER_BUSY", "A browser page is already open for another conversation.")))
            } else {
                controller.open(url, conversationId, aiCall = true).toSimpleToolResult { buildJsonObject { put("status", "browser_navigated") } }
            }
        },
    ),
    Tool(
        name = "browser_done",
        description = "Declare that the browser task is complete. This does not close the page or erase its login state.",
        parameters = { emptySchema() },
        execute = {
            controller.accessError(conversationId)?.let { return@Tool listOf(errorText(it)) }
            controller.endTask(conversationId)?.let { return@Tool listOf(errorText(it)) }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("status", "browser_done")
                put("message", "The browser task is complete. The page remains open for the user.")
            }.toString()))
        },
    ),
    )
    val byName = tools.associateBy(Tool::name)
    val disabled = settingsStore.settingsFlow.value.browserToolsDisabled
    return BrowserToolRegistry.tools
        .asSequence()
        .filter { BrowserToolRegistry.isEnabled(disabled, it.name) }
        .mapNotNull { byName[it.name] }
        .toList()
}

private fun emptySchema() = InputSchema.Obj(properties = buildJsonObject {})

private fun requiredSchema(name: String, description: String) = InputSchema.Obj(
    properties = buildJsonObject {
        put(name, buildJsonObject { put("type", "string"); put("description", description) })
    },
    required = listOf(name),
)

private fun requiredString(args: kotlinx.serialization.json.JsonElement, name: String): String =
    args.jsonObject[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: error("$name is required")

private fun errorText(error: Throwable): UIMessagePart.Text = UIMessagePart.Text(
    buildJsonObject {
        put("error", (error as? BrowserException)?.code ?: "BROWSER_ERROR")
        put("ok", false)
        put("message", (error as? BrowserException)?.message ?: "The browser action failed. Wait for the page to load and try again.")
    }.toString()
)

private suspend fun <T> Result<T>.toSimpleToolResult(
    success: suspend (T) -> kotlinx.serialization.json.JsonObject = { buildJsonObject { put("success", true) } },
): List<UIMessagePart> = fold(
    onSuccess = { listOf(UIMessagePart.Text(success(it).toString())) },
    onFailure = { listOf(errorText(it)) },
)

private suspend fun Result<String>.toFilteredTextToolResult(
    settingsStore: SettingsStore,
    controller: BrowserController,
    success: (String, Int) -> kotlinx.serialization.json.JsonObject,
): List<UIMessagePart> = fold(
    onSuccess = { raw ->
        val filtered = filterBrowserContent(settingsStore, raw)
        controller.recordFilteredCount(filtered.hitCount)
        listOf(UIMessagePart.Text(success(filtered.text, filtered.hitCount).toString()))
    },
    onFailure = { listOf(errorText(it)) },
)

private suspend fun Result<List<me.rerere.rikkahub.browser.BrowserClickable>>.toFilteredClickableToolResult(
    settingsStore: SettingsStore,
    controller: BrowserController,
    success: (List<me.rerere.rikkahub.browser.BrowserClickable>, Int) -> kotlinx.serialization.json.JsonObject,
): List<UIMessagePart> = fold(
    onSuccess = { list ->
        if (settingsStore.browserFilterEnabled()) {
            val results = list.map { CredentialFilter.filter(it.text) }
            val filtered = list.zip(results) { item, text -> item.copy(text = text.text) }
            val count = results.sumOf { it.hitCount }
            controller.recordFilteredCount(count)
            listOf(UIMessagePart.Text(success(filtered, count).toString()))
        } else {
            controller.recordFilteredCount(0)
            listOf(UIMessagePart.Text(success(list, 0).toString()))
        }
    },
    onFailure = { listOf(errorText(it)) },
)

private suspend fun SettingsStore.browserFilterEnabled(): Boolean =
    browserFilterEnabledFlow.first()

/** Shared by manual feeding and tool results, before either enters the conversation. */
internal suspend fun filterBrowserContent(settingsStore: SettingsStore, raw: String): CredentialFilter.FilteredResult =
    if (settingsStore.browserFilterEnabled()) CredentialFilter.filter(raw) else CredentialFilter.FilteredResult(raw, 0)
