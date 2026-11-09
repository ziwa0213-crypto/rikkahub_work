package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.db.dao.UsageTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PulseCostTest {
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
}
