package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.PulseRunLog
import me.rerere.rikkahub.data.db.dao.UsageTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PulseCostTest {
    @Test fun dailyPeakSummaryExcludesOtherDaysAndIncludesMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDate.of(2026, 11, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val config = PulseConfig(peakPricingEnabled = true, runLogs = listOf(
            PulseRunLog(at = start - 1, promptTokens = 900),
            PulseRunLog(at = start, promptTokens = 10),
            PulseRunLog(at = start + 3_600_000, completionTokens = 20),
            PulseRunLog(at = start + 86_400_000, promptTokens = 800),
        ))
        val summary = calculatePulseDailyCost(UsageTotals(), config, start, zone)
        assertEquals(10L, summary.inputTokens)
        assertEquals(20L, summary.outputTokens)
        // The settings page can still show its original recent-runs estimate.
        assertEquals(1710L, calculatePulseCost(UsageTotals(), config, config.runLogs).inputTokens)
    }

    @Test fun dailyWindowUsesLocalMidnightAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 1)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val config = PulseConfig(peakPricingEnabled = true, runLogs = listOf(
            PulseRunLog(at = end - 1, promptTokens = 7),
            PulseRunLog(at = end, promptTokens = 90),
        ))
        assertEquals(25 * 3_600_000L, end - start)
        assertEquals(7L, calculatePulseDailyCost(UsageTotals(), config, start, zone).inputTokens)
    }

    @Test fun dailyNonPeakSummaryUsesTheDatabaseTotals() {
        val totals = UsageTotals(promptTokens = 70, completionTokens = 30, cachedTokens = 10)
        val config = PulseConfig(runLogs = listOf(PulseRunLog(promptTokens = 999)))
        assertEquals(calculatePulseCost(totals, config), calculatePulseDailyCost(totals, config, 0))
    }

    @Test
    fun cachedTokensAreASubsetOfPromptAndNotDoubleBilled() {
        val summary = calculatePulseCost(
            UsageTotals(promptTokens = 1_000_000, completionTokens = 500_000, cachedTokens = 400_000),
            PulseConfig(priceInputPerMillion = 2.0, priceOutputPerMillion = 8.0, priceCachedPerMillion = 0.5),
        )
        assertEquals(5.4, summary.costYuan!!, 0.000001)
    }

    @Test
    fun badCachedCountsAreClampedToPromptCount() {
        val summary = calculatePulseCost(
            UsageTotals(promptTokens = 10, completionTokens = 0, cachedTokens = 20),
            PulseConfig(priceInputPerMillion = 1.0, priceCachedPerMillion = 2.0),
        )
        assertEquals(0.00002, summary.costYuan!!, 0.0000001)
    }

    @Test
    fun allZeroPricesDoNotShowAZeroAmount() {
        assertNull(calculatePulseCost(UsageTotals(10, 20, 5), PulseConfig()).costYuan)
    }

    @Test
    fun multiplierAppliesToWholeEstimate() {
        val summary = calculatePulseCost(
            UsageTotals(promptTokens = 1_000_000, completionTokens = 1_000_000),
            PulseConfig(priceInputPerMillion = 2.0, priceOutputPerMillion = 8.0, priceMultiplier = 2.0),
        )
        assertEquals(20.0, summary.costYuan!!, 0.000001)
    }

    @Test
    fun peakPricingUsesEachRunTimestampAndItsMatchingSegment() {
        val weekday = PulsePriceSegment(
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            days = listOf(1, 2, 3, 4, 5),
            inputPrice = 2.0,
            outputPrice = 8.0,
            cachedPrice = 0.5,
        )
        val config = PulseConfig(peakPricingEnabled = true, priceSegments = listOf(weekday))
        val weekdayLog = PulseRunLog(
            at = java.time.LocalDateTime.parse("2026-11-09T10:00:00").atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            promptTokens = 1_000_000,
            completionTokens = 500_000,
            cachedTokens = 400_000,
        )
        val outsideSegmentLog = weekdayLog.copy(at = java.time.LocalDateTime.parse("2026-11-09T20:00:00").atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())

        val summary = calculatePulseCost(UsageTotals(), config, listOf(weekdayLog, outsideSegmentLog))

        assertEquals(2_000_000L, summary.inputTokens)
        assertEquals(1_000_000L, summary.outputTokens)
        assertEquals(800_000L, summary.cachedTokens)
        assertEquals(5.4, summary.costYuan!!, 0.000001)
    }

    @Test
    fun peakModeWithNoSegmentsProducesZeroEstimate() {
        val summary = calculatePulseCost(UsageTotals(), PulseConfig(peakPricingEnabled = true), listOf(
            PulseRunLog(promptTokens = 100, completionTokens = 20),
        ))
        assertEquals(0.0, summary.costYuan!!, 0.0)
    }
}
