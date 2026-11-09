package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.PulseTaskRepository

internal fun pulseTaskTool(repository: PulseTaskRepository) = Tool(
    name = "schedule_task",
    description = "Schedule a task for Pulse. Use the current local date, time and IANA timezone in the context. Once tasks must be over one minute in the future.",
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
