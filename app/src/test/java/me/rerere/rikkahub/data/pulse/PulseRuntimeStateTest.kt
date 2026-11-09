package me.rerere.rikkahub.data.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class PulseRuntimeStateTest {
    @Test fun `day rollover resets count and last occurrence only`() {
        val current = PulseRuntimeState(
            todaySuccessCount = 9,
            todayKey = "2026-11-03",
            nextHeartbeatAt = 1234,
            lastHeartbeatAt = 1000,
            lastHeartbeatOk = false,
            lastOfDay = true,
        )

        val rolled = current.rolloverIfNeeded(LocalDate.parse("2026-11-04"))

        assertEquals("2026-11-04", rolled.todayKey)
        assertEquals(0, rolled.todaySuccessCount)
        assertFalse(rolled.lastOfDay)
        assertEquals(current.nextHeartbeatAt, rolled.nextHeartbeatAt)
        assertEquals(current.lastHeartbeatAt, rolled.lastHeartbeatAt)
        assertEquals(current.lastHeartbeatOk, rolled.lastHeartbeatOk)
    }
}
