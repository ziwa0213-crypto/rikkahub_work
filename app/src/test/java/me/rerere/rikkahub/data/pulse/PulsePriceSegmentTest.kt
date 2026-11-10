package me.rerere.rikkahub.data.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PulsePriceSegmentTest {
    private val mondayNight = PulsePriceSegment(startMinute = 22 * 60, endMinute = 6 * 60, days = listOf(1))

    @Test
    fun crossMidnightWindowUsesConfiguredCurrentWeekday() {
        val mondayAt23 = Instant.parse("2026-11-09T23:00:00Z").toEpochMilli()
        val tuesdayAt02 = Instant.parse("2026-11-10T02:00:00Z").toEpochMilli()
        val tuesdayAt06 = Instant.parse("2026-11-10T06:00:00Z").toEpochMilli()
        val config = me.rerere.rikkahub.data.datastore.PulseConfig(priceSegments = listOf(mondayNight))

        assertEquals(mondayNight, config.priceSegmentOf(mondayAt23, ZoneId.of("UTC")))
        assertNull(config.priceSegmentOf(tuesdayAt02, ZoneId.of("UTC")))
        assertEquals(mondayNight, config.priceSegmentOf(Instant.parse("2026-11-09T02:00:00Z").toEpochMilli(), ZoneId.of("UTC")))
        assertNull(config.priceSegmentOf(tuesdayAt06, ZoneId.of("UTC")))
    }

    @Test
    fun adjacentBoundariesDoNotOverlap() {
        val morning = PulsePriceSegment(startMinute = 9 * 60, endMinute = 12 * 60, days = listOf(1, 2, 3, 4, 5))
        val afternoon = PulsePriceSegment(startMinute = 12 * 60, endMinute = 18 * 60, days = listOf(1, 2, 3, 4, 5))
        assertFalse(pulsePriceSegmentsOverlap(morning, afternoon))
    }

    @Test
    fun crossMidnightOverlapUsesConfiguredDaysAndFullDayIsDetected() {
        val earlyTuesday = PulsePriceSegment(startMinute = 1 * 60, endMinute = 4 * 60, days = listOf(2))
        val earlyMonday = earlyTuesday.copy(days = listOf(1))
        val fullMonday = PulsePriceSegment(startMinute = 0, endMinute = 0, days = listOf(1))
        assertFalse(pulsePriceSegmentsOverlap(mondayNight, earlyTuesday))
        assertTrue(pulsePriceSegmentsOverlap(mondayNight, earlyMonday))
        assertTrue(pulsePriceSegmentsOverlap(fullMonday, mondayNight))
    }

    @Test
    fun differentDaySetsDoNotOverlap() {
        val weekday = PulsePriceSegment(startMinute = 9 * 60, endMinute = 12 * 60, days = listOf(1, 2, 3, 4, 5))
        val weekend = PulsePriceSegment(startMinute = 10 * 60, endMinute = 11 * 60, days = listOf(6, 7))
        assertFalse(pulsePriceSegmentsOverlap(weekday, weekend))
    }

    @Test
    fun editingSkipsItselfButDetectsAnotherSegment() {
        val segment = PulsePriceSegment(id = "one", days = emptyList())
        assertNull(pulsePriceSegmentConflict(segment, listOf(segment)))
        val other = segment.copy(id = "two")
        assertEquals(other, pulsePriceSegmentConflict(segment, listOf(segment, other)))
    }

    @Test
    fun timeValidationAcceptsMidnightAndRejectsInvalidClockValues() {
        assertEquals(0, me.rerere.rikkahub.ui.pages.pulse.parsePulsePriceTime("00:00"))
        assertEquals(1439, me.rerere.rikkahub.ui.pages.pulse.parsePulsePriceTime("23:59"))
        listOf("24:00", "12:60", "-1:00", "1:00", "abc").forEach {
            assertNull(me.rerere.rikkahub.ui.pages.pulse.parsePulsePriceTime(it))
        }
    }

    @Test
    fun weeklyUncoveredTimeUsesQuarterHourSlots() {
        val allDay = PulsePriceSegment(startMinute = 0, endMinute = 0, days = emptyList())
        assertEquals(0, uncoveredPulseQuarterHours(listOf(allDay)))
        assertEquals(672, uncoveredPulseQuarterHours(emptyList()))
        assertEquals(24.0, uncoveredPulseQuarterHours(listOf(
            PulsePriceSegment(startMinute = 0, endMinute = 0, days = listOf(1, 2, 3, 4, 5, 6)),
        )) * 0.25, 0.0)
    }
}
