package me.rerere.rikkahub.ui.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.pulse.PulseRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class PulseNotificationTextTest {
    private val zone = ZoneId.of("UTC")
    private val strings = PulseNotificationStrings(
        running = "running", heartbeat = "heartbeat", failed = "failed", idle = "paused",
        count = { count, time -> "$count|next $time" },
        countTomorrow = { count, time -> "$count|tomorrow $time" },
        countNone = { time -> "none|next $time" },
        runningLine = { count -> "$count|running" },
        lastOfDay = { count, time -> "$count|last $time" },
        lastOfDayRunning = { count -> "$count|running last" },
        idleLine = { "uptime $it" },
        durationHoursMinutes = { hours, minutes -> "${hours}h ${minutes}m" },
        durationMinutes = { "${it}m" },
    )
    private val clockTime: (ZonedDateTime) -> String = { "%02d:%02d".format(it.hour, it.minute) }

    private fun render(
        config: PulseConfig,
        state: PulseRuntimeState,
        now: String,
        elapsed: Long = 0,
    ) = renderPulseNotification(
        config = config,
        state = state,
        nowEpochMillis = Instant.parse("2026-11-03T$now:00Z").toEpochMilli(),
        nowElapsedMillis = elapsed,
        strings = strings,
        zone = zone,
        timeFormatter = clockTime,
    )

    @Test fun `active hours show no heartbeat and next time`() {
        val text = render(PulseConfig(enableHeartbeat = true), PulseRuntimeState(nextHeartbeatAt = Instant.parse("2026-11-03T08:30:00Z").toEpochMilli()), "08:00")
        assertEquals(PulseNotificationText("running", "none|next 08:30"), text)
    }

    @Test fun `active hours show successful count and next day clearly`() {
        val state = PulseRuntimeState(todaySuccessCount = 28, nextHeartbeatAt = Instant.parse("2026-11-04T08:00:00Z").toEpochMilli())
        assertEquals("28|tomorrow 08:00", render(PulseConfig(enableHeartbeat = true), state, "21:30").body)
    }

    @Test fun `last scheduled heartbeat remains marked while running`() {
        val state = PulseRuntimeState(todaySuccessCount = 27, runningSince = 1, runningLabel = "heartbeat", lastOfDay = true)
        assertEquals(PulseNotificationText("heartbeat", "27|running last"), render(PulseConfig(enableHeartbeat = true), state, "21:30"))
    }

    @Test fun `last failure stays visible until next success`() {
        val state = PulseRuntimeState(todaySuccessCount = 3, nextHeartbeatAt = Instant.parse("2026-11-03T10:30:00Z").toEpochMilli(), lastHeartbeatOk = false)
        assertEquals(PulseNotificationText("failed", "3|next 10:30"), render(PulseConfig(enableHeartbeat = true), state, "10:00"))
    }

    @Test fun `paused hours report elapsed service instance uptime`() {
        val state = PulseRuntimeState(serviceStartedAt = 10_000)
        val now = Instant.parse("2026-11-03T23:00:00Z").toEpochMilli()
        assertEquals("paused", render(PulseConfig(enableHeartbeat = true, activeHoursStart = 8, activeHoursEnd = 22), state, "23:00", now - 10_000).title)
        assertEquals("uptime 3h 12m", render(PulseConfig(enableHeartbeat = true, activeHoursStart = 8, activeHoursEnd = 22), state, "23:00", 10_000 + 3 * 3_600_000L + 12 * 60_000L + 1_000).body)
    }

    @Test fun `overnight and all day schedules use shared active hours`() {
        val state = PulseRuntimeState(nextHeartbeatAt = Instant.parse("2026-11-04T00:30:00Z").toEpochMilli())
        assertEquals("running", render(PulseConfig(enableHeartbeat = true, activeHoursStart = 22, activeHoursEnd = 6), state, "23:00").title)
        assertEquals("paused", render(PulseConfig(enableHeartbeat = true, activeHoursStart = 22, activeHoursEnd = 6), state, "06:30").title)
        assertEquals("running", render(PulseConfig(enableHeartbeat = true, activeHoursStart = 8, activeHoursEnd = 8), state, "06:30").title)
    }
}
