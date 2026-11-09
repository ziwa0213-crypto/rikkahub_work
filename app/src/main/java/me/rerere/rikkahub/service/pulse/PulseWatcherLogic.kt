package me.rerere.rikkahub.service.pulse

import me.rerere.rikkahub.data.datastore.PulseWatcher
import java.security.MessageDigest

internal data class WatcherBatteryResult(
    val baseline: Double,
    val changed: Boolean,
)

internal fun watcherBatteryResult(baseline: Double, current: Double, threshold: Double): WatcherBatteryResult {
    if (baseline < 0) return WatcherBatteryResult(current, false)
    val delta = kotlin.math.abs(current - baseline)
    if (delta < threshold.coerceIn(1.0, 50.0)) return WatcherBatteryResult(baseline, false)
    return WatcherBatteryResult(current, true)
}

internal fun watcherChanged(previous: String, current: String): Boolean =
    previous.isNotEmpty() && previous != current

internal fun watcherFingerprint(content: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(content)
        .take(8)
        .joinToString("") { "%02x".format(it) }

internal fun watcherIsDue(lastCheckedAt: Long, everyMinutes: Int, now: Long): Boolean =
    lastCheckedAt == 0L || now - lastCheckedAt >= everyMinutes.coerceIn(10, 240) * 60_000L

internal fun duePulseWatchers(watchers: List<PulseWatcher>, now: Long, limit: Int = 5): List<PulseWatcher> =
    watchers.filter { it.enabled && watcherIsDue(it.lastCheckedAt, it.everyMinutes, now) }.take(limit.coerceAtLeast(0))

