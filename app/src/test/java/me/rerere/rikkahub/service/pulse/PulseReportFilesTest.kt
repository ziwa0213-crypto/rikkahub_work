package me.rerere.rikkahub.service.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseReportFilesTest {
    @Test
    fun reportNamesHaveMinutePrecisionAndCollisionSuffix() {
        assertTrue(pulseReportFileName(1_793_604_622_000, "OK").matches(Regex("\\d{4}-\\d{2}-\\d{2}-\\d{4}-ok\\.txt")))
        assertTrue(pulseReportFileName(1_793_604_622_000, "OK", 2).endsWith("-ok-2.txt"))
        assertTrue(pulseReportFileName(1_793_604_622_000, "REPORTED").endsWith("-partial.txt"))
        assertTrue(pulseReportFileName(1_793_604_622_000, "FAILED").endsWith("-failed.txt"))
    }

    @Test
    fun retentionOnlySelectsOldestFilesWithOwnedNames() {
        val files = listOf(
            "note.txt", "2026-11-01-1000-ok.txt", "2026-11-02-1000-failed.txt",
            "2026-11-03-1000-partial.txt", "2026-11-04-1000-ok.txt", "2026-11-05-1000-ok.txt",
            "2026-11-06-1000-ok.txt",
        )
        assertEquals(listOf("2026-11-01-1000-ok.txt"), pulseReportRetention(files, keep = 5))
        assertFalse(isPulseReportFileName("note.txt"))
    }

    @Test
    fun retentionHandlesZeroAndOrdersCollisionSequenceNumerically() {
        val files = listOf("2026-11-06-1000-ok-10.txt", "2026-11-06-1000-ok-2.txt", "note.txt")
        assertEquals(listOf("2026-11-06-1000-ok-2.txt", "2026-11-06-1000-ok-10.txt"), pulseReportRetention(files, keep = 0))
        assertEquals(listOf("2026-11-06-1000-ok-2.txt"), pulseReportRetention(files, keep = 1))
    }
}
