package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.PulseTaskRepository

internal fun pulseTaskTool(repository: PulseTaskRepository) = Tool(
    name = "schedule_task",
    description = """
        Schedule, inspect, reschedule or cancel tasks that Pulse will run later on its own.
        action=list: inspect task ids before update/delete.
        action=create (default): needs title, prompt and when.
        action=update: needs id; pass only fields to change, including enabled to pause/resume.
        action=delete: needs id; this cannot be undone.
        Use the current local date, time and IANA timezone from the context when computing any time.

        - when=once: also set execute_at. Must be over one minute and at most one year in the future.
        - when=cron: also set cron. Use this for anything recurring ("every day at 9", "every Monday", "every hour").
          Five space-separated fields: minute hour day month weekday.
          Examples: "0 9 * * *" daily 09:00, "0 9 * * 1-5" weekdays 09:00, "0 * * * *" hourly, "*/30 * * * *" every 30 minutes.
        - when=heartbeat: runs with every heartbeat, no schedule.

        Prefer when=cron over creating many one-off tasks.
    """.trimIndent(),
    parameters = { InputSchema.Obj(buildJsonObject {
        for ((name, description) in mapOf("title" to "One-line summary", "prompt" to "What to do", "when" to "once | cron | heartbeat",
            "action" to "list | create | update | delete; default create", "id" to "Task id from action=list",
            "execute_at" to "ISO-8601 local time, preferably with UTC offset", "cron" to "Five fields: minute hour day month weekday")) {
            put(name, buildJsonObject { put("type", "string"); put("description", description) })
        }
        put("enabled", buildJsonObject { put("type", "boolean"); put("description", "Enable or pause the task") })
    }) },
    execute = { value ->
        val args = value.jsonObject
        fun text(name: String) = (args[name] as? JsonPrimitive)?.contentOrNull
        val result = when (text("action") ?: "create") {
            "list" -> buildJsonArray { repository.list().forEach { task -> add(buildJsonObject {
                put("id", task.id); put("title", task.title)
                put("when", when (task.kind) {
                    me.rerere.rikkahub.data.datastore.PulseTaskKind.ONCE -> "once"
                    me.rerere.rikkahub.data.datastore.PulseTaskKind.CRON -> "cron"
                    me.rerere.rikkahub.data.datastore.PulseTaskKind.ON_HEARTBEAT -> "heartbeat"
                })
                put("cron", task.cron); put("next_at", task.nextAt); put("next_at_local", pulseTaskLocalTime(task))
                put("enabled", task.enabled); put("last_result", task.lastResult); put("prompt", task.prompt.take(200))
            }) } }
            "create", "update" -> {
                val create = (text("action") ?: "create") == "create"
                val task = if (create) repository.create(text("title").orEmpty(), text("prompt").orEmpty(), text("when").orEmpty(), text("execute_at"), text("cron"))
                    else repository.update(requireNotNull(text("id")) { "id is required" }, text("title"), text("prompt"), text("when"),
                        text("execute_at"), text("cron"), args["enabled"]?.let { requireNotNull((it as? JsonPrimitive)?.booleanOrNull) { "enabled must be boolean" } })
                buildJsonObject { put("id", task.id); put("next_at", task.nextAt); put("next_at_local", pulseTaskLocalTime(task)); put(if (create) "created" else "updated", true) }
            }
            "delete" -> {
                val id = requireNotNull(text("id")) { "id is required" }
                buildJsonObject { put("id", id); put("deleted", repository.delete(id)) }
            }
            else -> error("action must be list, create, update or delete")
        }
        listOf(UIMessagePart.Text(result.toString()))
    },
)

private fun pulseTaskLocalTime(task: me.rerere.rikkahub.data.datastore.PulseTask): String =
    if (task.nextAt <= 0) "" else java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(runCatching { java.time.ZoneId.of(task.timeZoneId) }.getOrDefault(java.time.ZoneId.systemDefault()))
        .format(java.time.Instant.ofEpochMilli(task.nextAt))
