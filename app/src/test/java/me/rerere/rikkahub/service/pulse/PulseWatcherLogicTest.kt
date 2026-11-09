package me.rerere.rikkahub.service.pulse

import me.rerere.rikkahub.data.datastore.PulseWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseWatcherLogicTest {
    @Test
    fun firstFingerprintOnlyEstablishesBaseline() {
        assertFalse(watcherChanged("", "first"))
        assertTrue(watcherChanged("first", "second"))
        assertFalse(watcherChanged("same", "same"))
    }

    @Test
    fun fingerprintIsStableAndContentSensitive() {
        assertEquals(watcherFingerprint("same".toByteArray()), watcherFingerprint("same".toByteArray()))
        assertNotEquals(watcherFingerprint("same".toByteArray()), watcherFingerprint("changed".toByteArray()))
        assertEquals(16, watcherFingerprint(byteArrayOf()).length)
    }

    @Test
    fun batteryBaselineChangesOnlyOnFirstSampleOrThresholdDrop() {
        assertEquals(80.0, watcherBatteryResult(-1.0, 80.0, 5.0).baseline, 0.0)
        val smallDrop = watcherBatteryResult(80.0, 78.0, 5.0)
        assertFalse(smallDrop.changed)
        assertEquals(80.0, smallDrop.baseline, 0.0)
        val upwardChange = watcherBatteryResult(80.0, 85.0, 5.0)
        assertTrue(upwardChange.changed)
        assertEquals(85.0, upwardChange.baseline, 0.0)
        val thresholdDrop = watcherBatteryResult(80.0, 75.0, 5.0)
        assertTrue(thresholdDrop.changed)
        assertEquals(75.0, thresholdDrop.baseline, 0.0)
    }

    @Test
    fun watcherIntervalIsClampedToTenThroughTwoHundredFortyMinutes() {
        assertTrue(watcherIsDue(1_000, 10, 1_000 + 10 * 60_000L))
        assertFalse(watcherIsDue(1_000, 10, 1_000 + 9 * 60_000L))
        assertFalse(watcherIsDue(1_000, 1, 1_000 + 9 * 60_000L))
        assertTrue(watcherIsDue(1_000, 500, 1_000 + 240 * 60_000L))
    }

    @Test
    fun eachTickSamplesNoMoreThanFiveEnabledDueWatchers() {
        val watchers = (1..8).map { PulseWatcher(id = "w$it", enabled = it != 2, lastCheckedAt = 0L) }

        val selected = duePulseWatchers(watchers, now = 1_000L)

        assertEquals(5, selected.size)
        assertTrue(selected.none { it.id == "w2" })
    }
}
