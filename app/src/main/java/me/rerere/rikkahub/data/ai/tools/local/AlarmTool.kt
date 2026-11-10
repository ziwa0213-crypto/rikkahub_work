package me.rerere.rikkahub.data.ai.tools.local

import android.content.Intent
import android.provider.AlarmClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.AlarmForegroundActivity

internal fun buildAlarmTool(): Tool = Tool(
    name = "set_alarm",
    description = "Request a system alarm or countdown timer. Alarms require hour and minute; timers require duration_seconds (1–86400). " +
        "Create only: cannot list, modify or delete alarms. Requires the app to be in the foreground. " +
        "Success only confirms the request was sent, not that the Clock app saved it; system confirmation may still be required.",
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {
            put("hour", buildJsonObject { put("type", "integer"); put("minimum", 0); put("maximum", 23) })
            put("minute", buildJsonObject { put("type", "integer"); put("minimum", 0); put("maximum", 59) })
            put("label", buildJsonObject { put("type", "string"); put("description", "Optional alarm or timer label") })
            put("duration_seconds", buildJsonObject {
                put("type", "integer"); put("minimum", 1); put("maximum", 86400)
                put("description", "Countdown duration, takes precedence over hour and minute")
            })
        })
    },
    execute = { input ->
        val request = runCatching { parseAlarmRequest(input) }
        val result = if (request.isFailure) alarmFailure(request.exceptionOrNull()?.message ?: "Invalid alarm parameters.")
        else withContext(Dispatchers.Main.immediate) {
            val activity = AlarmForegroundActivity.current()
            dispatchAlarmRequest(request.getOrThrow(), foreground = activity != null) { alarm ->
                val intent = Intent(if (alarm.durationSeconds != null) AlarmClock.ACTION_SET_TIMER else AlarmClock.ACTION_SET_ALARM).apply {
                    if (alarm.durationSeconds != null) putExtra(AlarmClock.EXTRA_LENGTH, alarm.durationSeconds)
                    else {
                        putExtra(AlarmClock.EXTRA_HOUR, requireNotNull(alarm.hour))
                        putExtra(AlarmClock.EXTRA_MINUTES, requireNotNull(alarm.minute))
                    }
                    alarm.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                requireNotNull(activity).startActivity(intent)
            }
        }
        listOf(UIMessagePart.Text(result.toString()))
    },
)
