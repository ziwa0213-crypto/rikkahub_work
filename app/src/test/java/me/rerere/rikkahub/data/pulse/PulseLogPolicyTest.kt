package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseActivityEntry
import me.rerere.rikkahub.data.datastore.PulseRunLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseLogPolicyTest {
    @Test
    fun runLogIsCappedAtTwoHundredAndNewestComesFirst() {
        val old = (1..205).reversed().map { PulseRunLog(at = it.toLong(), note = "old") }
        val newest = PulseRunLog(at = 1_000, note = "x".repeat(240))

        val result = appendPulseRunLog(old, newest)

        assertEquals(200, result.size)
        assertEquals(1_000L, result.first().at)
        assertEquals(7L, result.last().at)
        assertEquals(200, result.first().note.length)
    }

    @Test
    fun activityLogIsCappedAtTwentyAndDoesNotStoreLongMessageBodies() {
        val old = (1..25).reversed().map { PulseActivityEntry(at = it.toLong(), summary = "old") }
        val newest = PulseActivityEntry(at = 1_000, summary = "x".repeat(300))

        val result = appendPulseActivityLog(old, newest)

        assertEquals(20, result.size)
        assertEquals(1_000L, result.first().at)
        assertEquals(7L, result.last().at)
        assertTrue(result.first().summary.length <= 200)
    }
}
