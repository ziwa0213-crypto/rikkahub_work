package me.rerere.rikkahub.service.pulse

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

import java.time.Instant
import java.time.ZoneId
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.isInActiveHours

internal const val PULSE_HEARTBEAT_RECHECK_MS = 60_000L
internal const val PULSE_ACTIVE_GENERATION_RECHECK_MS = 60_000L

internal fun pulseDispatchTarget(candidate: Long, now: Long, generationActive: Boolean): Long =
    candidate.coerceAtLeast(now + if (generationActive) PULSE_ACTIVE_GENERATION_RECHECK_MS else 1_000L)

internal fun pulseWithinHours(now: Long, start: Int, end: Int, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val config = PulseConfig(activeHoursStart = start, activeHoursEnd = end)
    return config.isInActiveHours(Instant.ofEpochMilli(now).atZone(zone).toLocalTime())
}

internal fun isSilentPulse(text: String): Boolean = text.trim().equals("HEARTBEAT_OK", ignoreCase = true)

internal fun isAwaitingPulseReply(messages: List<UIMessage>): Boolean =
    messages.lastOrNull()?.role == MessageRole.USER

/** A wakeup runs once whenever the interval has elapsed, regardless of how late it is. */
internal fun pulseHeartbeatDue(now: Long, previous: Long, interval: Long): Boolean =
    previous > 0 && now - previous >= interval

internal fun pulseNextActiveAt(at: Long, start: Int, end: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    if (pulseWithinHours(at, start, end, zone)) return at
    val local = Instant.ofEpochMilli(at).atZone(zone)
    var next = local.toLocalDate().atTime(start.coerceIn(0, 23), 0).atZone(zone)
    if (!next.isAfter(local)) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}

internal fun pulseNextHeartbeatAt(
    startedAt: Long,
    intervalMillis: Long,
    config: PulseConfig,
    zone: ZoneId = ZoneId.systemDefault(),
    notBefore: Long = 0L,
): Pair<Long, Boolean> {
    // Once due, the next active instant is sufficient; there is no aligned slot to wait for.
    val candidate = pulseNextActiveAt(
        maxOf(startedAt + intervalMillis, notBefore), config.activeHoursStart, config.activeHoursEnd, zone,
    )
    val local = Instant.ofEpochMilli(candidate).atZone(zone)
    val nextLocal = Instant.ofEpochMilli(candidate + intervalMillis).atZone(zone)
    val lastOfDay = nextLocal.toLocalDate() != local.toLocalDate() ||
        !config.isInActiveHours(nextLocal.toLocalTime())
    return candidate to lastOfDay
}
