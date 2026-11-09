package me.rerere.rikkahub.service.pulse

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.DEFAULT_HEARTBEAT_PROMPT
import me.rerere.rikkahub.data.ai.appendPulseInstruction
import me.rerere.ai.ui.UIMessage

class PulseScheduleTest {
    private val utc = ZoneId.of("UTC")
    private fun at(hour: String) = Instant.parse("2026-11-03T$hour:00Z").toEpochMilli()

    @Test fun `daytime includes start and excludes end`() {
        assertTrue(pulseWithinHours(at("08:00"), 8, 22, utc))
        assertFalse(pulseWithinHours(at("22:00"), 8, 22, utc))
    }
    @Test fun `midnight range includes midnight`() {
        assertTrue(pulseWithinHours(at("00:00"), 0, 8, utc))
        assertTrue(pulseWithinHours(at("07:59"), 0, 8, utc))
        assertFalse(pulseWithinHours(at("08:00"), 0, 8, utc))
    }
    @Test fun `cross midnight accepts both sides but excludes noon`() {
        assertTrue(pulseWithinHours(at("23:00"), 22, 6, utc))
        assertTrue(pulseWithinHours(at("03:00"), 22, 6, utc))
        assertFalse(pulseWithinHours(at("12:00"), 22, 6, utc))
    }
    @Test fun `equal hours mean all day`() {
        (0..23).forEach { hour -> assertTrue(pulseWithinHours(at("%02d:00".format(hour)), 8, 8, utc)) }
    }
    @Test fun `timezone uses local hour`() {
        assertTrue(pulseWithinHours(at("00:00"), 8, 9, ZoneId.of("Asia/Shanghai")))
        assertFalse(pulseWithinHours(at("00:00"), 8, 9, utc))
    }
    @Test fun `next active time crosses date and midnight range`() {
        assertEquals(Instant.parse("2026-11-04T08:00:00Z").toEpochMilli(), pulseNextActiveAt(at("23:00"), 8, 22, utc))
        assertEquals(at("22:00"), pulseNextActiveAt(at("12:00"), 22, 6, utc))
        assertEquals(at("03:00"), pulseNextActiveAt(at("03:00"), 22, 6, utc))
    }
    @Test fun `silent marker must occupy whole trimmed reply`() {
        assertTrue(isSilentPulse(" \nheartbeat_ok\t"))
        assertFalse(isSilentPulse("HEARTBEAT_OK but a deadline is due"))
        assertFalse(isSilentPulse(""))
        assertFalse(isSilentPulse("`HEARTBEAT_OK`"))
    }

    @Test fun `unanswered user message blocks automatic heartbeat`() {
        assertTrue(isAwaitingPulseReply(listOf(UIMessage.user("pending"))))
        assertFalse(isAwaitingPulseReply(listOf(UIMessage.assistant("reply"))))
    }

    @Test fun `due dispatch backs off while a user generation is active`() {
        val now = 10_000L
        assertEquals(now + PULSE_ACTIVE_GENERATION_RECHECK_MS, pulseDispatchTarget(now, now, generationActive = true))
        assertEquals(now + 1_000L, pulseDispatchTarget(now, now, generationActive = false))
        assertEquals(now + 120_000L, pulseDispatchTarget(now + 120_000L, now, generationActive = true))
    }

    @Test fun `default heartbeat prompt is a system instruction`() {
        assertFalse(DEFAULT_HEARTBEAT_PROMPT.startsWith("[HEARTBEAT]"))
        assertTrue(DEFAULT_HEARTBEAT_PROMPT.contains("HEARTBEAT_OK"))
        val system = StringBuilder("persona")
        appendPulseInstruction(system, DEFAULT_HEARTBEAT_PROMPT)
        assertEquals("persona", system.toString().substringBefore("\n\n"))
        assertTrue(system.indexOf("HEARTBEAT_OK") > 0)
    }
    @Test fun `late wakeup runs once when interval elapsed`() {
        val start = at("08:00")
        val interval = 30 * 60_000L
        assertTrue(pulseHeartbeatDue(start + interval, start, interval))
        assertFalse(pulseHeartbeatDue(start + interval - 1, start, interval))
        assertTrue(pulseHeartbeatDue(start + interval + 60_000, start, interval))
        assertTrue(pulseHeartbeatDue(start + 8 * interval, start, interval))
        assertFalse(pulseHeartbeatDue(start, 0, interval))
    }

    @Test fun `last daily heartbeat follows interval and half-open end`() {
        val halfHour = 30 * 60_000L
        assertEquals(
            at("21:30"),
            pulseNextHeartbeatAt(at("21:00"), halfHour, PulseConfig(activeHoursStart = 8, activeHoursEnd = 22), utc).first,
        )
        assertTrue(pulseNextHeartbeatAt(at("21:00"), halfHour, PulseConfig(activeHoursStart = 8, activeHoursEnd = 22), utc).second)

        val hour = 60 * 60_000L
        assertEquals(
            at("21:00"),
            pulseNextHeartbeatAt(at("20:00"), hour, PulseConfig(activeHoursStart = 8, activeHoursEnd = 22), utc).first,
        )
        assertTrue(pulseNextHeartbeatAt(at("20:00"), hour, PulseConfig(activeHoursStart = 8, activeHoursEnd = 22), utc).second)
        assertTrue(pulseNextHeartbeatAt(at("04:00"), hour, PulseConfig(activeHoursStart = 22, activeHoursEnd = 6), utc).second)
    }

    @Test fun `wake after hours runs at the next opening without interval alignment`() {
        val config = PulseConfig(activeHoursStart = 8, activeHoursEnd = 22)
        assertEquals(at("08:00"), pulseNextHeartbeatAt(at("06:17"), 30 * 60_000L, config, utc).first)
        val narrowWindow = PulseConfig(activeHoursStart = 8, activeHoursEnd = 9)
        assertEquals(at("08:00"), pulseNextHeartbeatAt(at("01:00"), 4 * 3_600_000L, narrowWindow, utc).first)
    }

    @Test fun `overdue wake runs now while skipped input waits for a bounded recheck`() {
        val config = PulseConfig(activeHoursStart = 0, activeHoursEnd = 0)
        val previous = at("08:00")
        val wake = at("14:00")
        val interval = 30 * 60_000L
        assertEquals(wake, pulseNextHeartbeatAt(previous, interval, config, utc, notBefore = wake).first)
        val retry = wake + PULSE_HEARTBEAT_RECHECK_MS
        assertEquals(retry, pulseNextHeartbeatAt(previous, interval, config, utc, notBefore = retry).first)
        assertEquals(previous + interval, pulseNextHeartbeatAt(previous, interval, config, utc, notBefore = previous).first)
    }
}
