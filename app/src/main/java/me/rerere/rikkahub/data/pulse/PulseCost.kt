package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.db.dao.UsageTotals
import kotlin.math.max

data class PulseCostSummary(val inputTokens: Long, val outputTokens: Long, val cachedTokens: Long, val costYuan: Double?) {
    val totalTokens: Long get() = inputTokens + outputTokens
}

fun calculatePulseCost(totals: UsageTotals, config: PulseConfig): PulseCostSummary {
    val input = totals.promptTokens.coerceAtLeast(0)
    val output = totals.completionTokens.coerceAtLeast(0)
    val cached = totals.cachedTokens.coerceAtLeast(0).coerceAtMost(input)
    val hasPrices = config.priceInputPerMillion > 0 || config.priceOutputPerMillion > 0 || config.priceCachedPerMillion > 0
    val cost = if (!hasPrices) null else {
        val billableInput = max(0L, input - cached)
        (billableInput / 1_000_000.0 * config.priceInputPerMillion.coerceAtLeast(0.0) +
            cached / 1_000_000.0 * config.priceCachedPerMillion.coerceAtLeast(0.0) +
            output / 1_000_000.0 * config.priceOutputPerMillion.coerceAtLeast(0.0)) *
            config.priceMultiplier.coerceAtLeast(0.0)
    }
    return PulseCostSummary(input, output, cached, cost)
}

fun compactPulseNumber(value: Long): String = when {
    value >= 1_000_000 -> "%.2fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.2fK".format(value / 1_000.0)
    else -> value.coerceAtLeast(0).toString()
}
