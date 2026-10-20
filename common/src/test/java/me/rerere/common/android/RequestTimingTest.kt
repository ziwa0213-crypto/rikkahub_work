package me.rerere.common.android

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RequestTimingTest {
    @Test fun awakeAndSuspendAreIndependentOfBootOffset() {
        val start = RequestClockSnapshot(1_000_000, 10_000)
        val end = RequestClockSnapshot(8_230_000, 40_000)
        assertEquals(7_230_000L, end.elapsedSince(start))
        assertEquals(30_000L, end.awakeSince(start))
        assertEquals(7_200_000L, end.suspendedSince(start))
    }

    @Test fun noSleepAndSamplingNoiseNeverProduceNegativeDuration() {
        val start = RequestClockSnapshot(100, 50)
        assertEquals(0L, RequestClockSnapshot(200, 150).suspendedSince(start))
        assertEquals(0L, RequestClockSnapshot(200, 151).suspendedSince(start))
        assertEquals(0L, RequestClockSnapshot(0, 0).awakeSince(start))
    }

    @Test fun oldRequestLogDecodesWithZeroSleep() {
        val log = Json.decodeFromString<LogEntry.RequestLog>("""{"tag":"HTTP","url":"https://example.test","method":"GET"}""")
        assertEquals(0L, log.suspendedMs)
        assertNull(log.durationMs)
    }
}
