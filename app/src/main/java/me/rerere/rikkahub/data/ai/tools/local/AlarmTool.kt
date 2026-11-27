package me.rerere.rikkahub.data.ai.tools.local

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

internal fun buildAlarmTool(context: Context): Tool = Tool(
    name = "set_alarm",
    description = "Request a system alarm or countdown timer. Alarms require hour and minute; timers require duration_seconds (1–86400). " +
        "Works in the background when the system allows background launches; otherwise the result says background_blocked. " +
        "Create only: cannot list, modify or delete alarms. Success only means the request was sent to the Clock app, not that it was saved. " +
        "verified is best-effort next-alarm evidence; false is inconclusive, including for timers and alarms later than the next alarm.",
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
            val appContext = context.applicationContext
            val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            dispatchAlarmRequest(
                request = request.getOrThrow(),
                backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !visible && !Settings.canDrawOverlays(appContext),
                backgroundLaunch = !visible,
                readNextAlarm = { alarmManager?.nextAlarmClock?.triggerTime },
            ) { alarm ->
                val intent = Intent(if (alarm.durationSeconds != null) AlarmClock.ACTION_SET_TIMER else AlarmClock.ACTION_SET_ALARM).apply {
                    if (alarm.durationSeconds != null) putExtra(AlarmClock.EXTRA_LENGTH, alarm.durationSeconds)
                    else {
                        putExtra(AlarmClock.EXTRA_HOUR, requireNotNull(alarm.hour))
                        putExtra(AlarmClock.EXTRA_MINUTES, requireNotNull(alarm.minute))
                    }
                    alarm.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }
        }
        listOf(UIMessagePart.Text(result.toString()))
    },
)
