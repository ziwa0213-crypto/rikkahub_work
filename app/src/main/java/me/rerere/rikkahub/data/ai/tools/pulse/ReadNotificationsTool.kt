package me.rerere.rikkahub.data.ai.tools.pulse

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.pulse.PulseNotification
import me.rerere.rikkahub.service.pulse.PulseNotificationInbox
import me.rerere.rikkahub.service.pulse.hasNotificationAccess
import java.time.Instant
import java.time.ZoneId

internal fun pulseJsonResult(value: JsonObject): List<UIMessagePart> = listOf(UIMessagePart.Text(value.toString()))

internal fun sensingPermissionDenied(): List<UIMessagePart> = pulseJsonResult(buildJsonObject {
    put("granted", false)
    put("hint", "Grant access in Pulse settings before reading.")
})

internal fun readNotificationsTool(context: Context, store: SettingsStore) = Tool(
    name = "read_notifications",
    description = "Read recent device notifications. Only whitelisted apps return content; other apps are counted only. Requires notification access. Results become part of conversation history.",
    parameters = { InputSchema.Obj(buildJsonObject {
        put("limit", buildJsonObject { put("type", "integer"); put("description", "1-20, default 10") })
        put("app", buildJsonObject { put("type", "string"); put("description", "Optional exact package name or application label") })
    }) },
    execute = { input ->
        val config = store.awaitLoaded().pulse
        if (!hasNotificationAccess(context)) {
            PulseNotificationInbox.buffer.clear()
            sensingPermissionDenied()
        } else if (!config.enableNotificationReading) {
            pulseJsonResult(buildJsonObject { put("granted", true); put("enabled", false) })
        } else {
            val args = input as? JsonObject
            pulseJsonResult(notificationResult(config, PulseNotificationInbox.buffer.snapshot(config),
                (args?.get("limit") as? JsonPrimitive)?.intOrNull ?: 10,
                (args?.get("app") as? JsonPrimitive)?.contentOrNull))
        }
    },
)

/** Bound the serialized JSON, including metadata and escaping, rather than only the plain text. */
internal fun notificationResult(config: PulseConfig, entries: List<PulseNotification>, limit: Int, app: String?): JsonObject {
    val filtered = entries.filter { app.isNullOrBlank() || it.packageName == app || it.appLabel.equals(app, ignoreCase = true) }
    val byApp = filtered.groupBy { it.packageName }.values.sortedByDescending { it.size }.take(10).toMutableList()
    val items = filtered.filter { !it.redacted && it.packageName in config.notificationWhitelist }
        .sortedByDescending { it.postedAt }.take(limit.coerceIn(1, 20)).toMutableList()
    fun json() = buildJsonObject {
        put("granted", true)
        put("hours", config.notificationRetentionHours.coerceIn(1, 72))
        put("total", filtered.size)
        putJsonArray("by_app") { byApp.forEach { group -> add(buildJsonObject {
            put("app", group.first().appLabel.take(80)); put("count", group.size)
        }) } }
        putJsonArray("items") { items.forEach { item -> add(buildJsonObject {
            put("app", item.appLabel.take(80))
            put("at", Instant.ofEpochMilli(item.postedAt).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
            item.title?.let { put("title", it.take(80)) }
            item.text?.let { put("text", it.take(200)) }
        }) } }
        if (filtered.any { it.redacted || it.packageName !in config.notificationWhitelist })
            put("redacted_note", "Apps not whitelisted are counted but their content is not read.")
    }
    var result = json()
    while (result.toString().length > 4000 && (items.isNotEmpty() || byApp.isNotEmpty())) {
        if (items.isNotEmpty()) items.removeAt(items.lastIndex) else byApp.removeAt(byApp.lastIndex)
        result = json()
    }
    return result
}
