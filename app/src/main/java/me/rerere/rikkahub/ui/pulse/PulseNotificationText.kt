package me.rerere.rikkahub.ui.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.isInActiveHours
import me.rerere.rikkahub.data.pulse.PulseRuntimeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

data class PulseNotificationText(val title: String, val body: String)

data class PulseNotificationStrings(
    val running: String,
    val heartbeat: String,
    val failed: String,
    val idle: String,
    val count: (Int, String) -> String,
    val countTomorrow: (Int, String) -> String,
    val countNone: (String) -> String,
    val runningLine: (Int) -> String,
    val lastOfDay: (Int, String) -> String,
    val lastOfDayRunning: (Int) -> String,
    val idleLine: (String) -> String,
    val durationHoursMinutes: (Int, Int) -> String,
    val durationMinutes: (Int) -> String,
)

fun renderPulseNotification(
    config: PulseConfig,
    state: PulseRuntimeState,
    nowEpochMillis: Long,
    nowElapsedMillis: Long,
    strings: PulseNotificationStrings,
    zone: ZoneId = ZoneId.systemDefault(),
    timeFormatter: (java.time.ZonedDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())) },
): PulseNotificationText {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val active = config.enableHeartbeat && config.isInActiveHours(now.toLocalTime())
    if (!active) {
        return PulseNotificationText(strings.idle, strings.idleLine(formatDuration(
            max(0L, nowElapsedMillis - state.serviceStartedAt), strings,
        )))
    }

    if (state.runningSince != 0L && state.runningLabel == "heartbeat") {
        val title = if (state.lastHeartbeatOk) strings.heartbeat else strings.failed
        return PulseNotificationText(title, if (state.lastOfDay) {
            strings.lastOfDayRunning(state.todaySuccessCount)
        } else strings.runningLine(state.todaySuccessCount))
    }

    val title = if (state.lastHeartbeatOk) strings.running else strings.failed
    val next = state.nextHeartbeatAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone) }
    if (next == null) return PulseNotificationText(title, strings.countNone("—"))
    val time = timeFormatter(next)
    val tomorrow = next.toLocalDate() != now.toLocalDate()
    val body = when {
        state.lastOfDay && !tomorrow -> strings.lastOfDay(state.todaySuccessCount, time)
        state.todaySuccessCount == 0 && !tomorrow -> strings.countNone(time)
        tomorrow -> strings.countTomorrow(state.todaySuccessCount, time)
        else -> strings.count(state.todaySuccessCount, time)
    }
    return PulseNotificationText(title, body)
}


private fun formatDuration(elapsed: Long, strings: PulseNotificationStrings): String {
    val minutes = elapsed / 60_000L
    return if (minutes < 60) strings.durationMinutes(minutes.toInt())
    else strings.durationHoursMinutes((minutes / 60).toInt(), (minutes % 60).toInt())
}
