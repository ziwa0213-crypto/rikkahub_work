package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.PulseTaskRepository

internal fun pulseTaskTool(repository: PulseTaskRepository) = Tool(
    name = "schedule_task",
    description = """
        Schedule a task that Pulse will run later on its own.
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
            "execute_at" to "ISO-8601 local time, preferably with UTC offset", "cron" to "Five fields: minute hour day month weekday")) {
            put(name, buildJsonObject { put("type", "string"); put("description", description) })
        }
    }, required = listOf("title", "prompt", "when")) },
    execute = { value ->
        val args = value.jsonObject
        fun text(name: String) = (args[name] as? JsonPrimitive)?.contentOrNull
        val task = repository.create(text("title").orEmpty(), text("prompt").orEmpty(), text("when").orEmpty(), text("execute_at"), text("cron"))
        listOf(UIMessagePart.Text(buildJsonObject { put("id", task.id); put("next_at", task.nextAt); put("created", true) }.toString()))
    },
)
