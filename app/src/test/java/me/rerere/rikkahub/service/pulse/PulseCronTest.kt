package me.rerere.rikkahub.service.pulse

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PulseCronTest {
    private val utc = ZoneId.of("UTC")
    private fun epoch(value: String) = Instant.parse(value).toEpochMilli()
    private fun next(cron: String, from: String, expected: String, zone: ZoneId = utc) {
        assertEquals(epoch(expected), PulseCron(cron).nextAfter(epoch(from), zone))
    }
    @Test fun `every minute is strictly in future`() = next("* * * * *", "2026-11-03T12:00:00Z", "2026-11-03T12:01:00Z")
    @Test fun `daily uses local timezone`() = next("0 9 * * *", "2026-11-03T00:00:00Z", "2026-11-03T01:00:00Z", ZoneId.of("Asia/Shanghai"))
    @Test fun `weekly monday`() = next("0 9 * * 1", "2026-11-03T12:00:00Z", "2026-11-09T09:00:00Z")
    @Test fun `quarter hour step`() = next("*/15 * * * *", "2026-11-03T12:14:30Z", "2026-11-03T12:15:00Z")
    @Test fun `range and comma list`() = next("5,10,20 8-10 * * *", "2026-11-03T08:10:00Z", "2026-11-03T08:20:00Z")
    @Test fun `cross month`() = next("0 0 1 * *", "2026-11-30T23:59:00Z", "2026-12-01T00:00:00Z")
    @Test fun `cross year`() = next("0 0 1 1 *", "2026-12-31T23:59:00Z", "2027-01-01T00:00:00Z")
    @Test fun `sunday aliases agree`() {
        val from = epoch("2026-11-03T12:00:00Z")
        assertEquals(PulseCron("0 9 * * 0").nextAfter(from, utc), PulseCron("0 9 * * 7").nextAfter(from, utc))
    }
    @Test fun `nonexistent date is not scheduled`() { assertNull(PulseCron("0 9 31 2 *").nextAfter(epoch("2026-11-03T12:00:00Z"), utc)) }
    @Test fun `unsupported and out of range syntax is rejected`() {
        listOf("", "* * * *", "* * * * * *", "60 * * * *", "* 24 * * *", "*/0 * * * *", "0 0 L * *", "0 0 * * ?", "0 0 * * 8", "0 9 3-1 * *").forEach {
            assertTrue("accepted: $it", runCatching { PulseCron(it) }.isFailure)
        }
    }
    @Test fun `spring forward skips nonexistent local time`() = next("30 2 * * *", "2026-03-08T06:00:00Z", "2026-03-09T06:30:00Z", ZoneId.of("America/New_York"))
}
