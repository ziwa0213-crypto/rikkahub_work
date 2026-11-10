package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.PulseRunLog
import me.rerere.rikkahub.data.db.dao.UsageTotals
import kotlin.math.max
import java.time.Instant
import java.time.ZoneId

data class PulseCostSummary(val inputTokens: Long, val outputTokens: Long, val cachedTokens: Long, val costYuan: Double?) {
    val totalTokens: Long get() = inputTokens + outputTokens
}

/** The drawer is a daily summary; peak estimates use only retained logs for that local day. */
fun calculatePulseDailyCost(
    totals: UsageTotals,
    config: PulseConfig,
    dayStart: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): PulseCostSummary {
    val dayEnd = Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate().plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    return calculatePulseCost(totals, config, config.runLogs.filter { it.at >= dayStart && it.at < dayEnd })
}

fun calculatePulseCost(
    totals: UsageTotals,
    config: PulseConfig,
    runLogs: List<PulseRunLog> = emptyList(),
): PulseCostSummary {
    if (config.peakPricingEnabled) return calculatePeakPulseCost(runLogs, config)

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

private fun calculatePeakPulseCost(logs: List<PulseRunLog>, config: PulseConfig): PulseCostSummary {
    val input = logs.sumOf { it.promptTokens.coerceAtLeast(0).toLong() }
    val output = logs.sumOf { it.completionTokens.coerceAtLeast(0).toLong() }
    val cached = logs.sumOf { it.cachedTokens.coerceAtLeast(0).coerceAtMost(it.promptTokens.coerceAtLeast(0)).toLong() }
    val cost = logs.sumOf { log ->
        val segment = config.priceSegmentOf(log.at) ?: return@sumOf 0.0
        val cachedTokens = log.cachedTokens.coerceAtLeast(0).coerceAtMost(log.promptTokens.coerceAtLeast(0))
        val uncachedInput = max(0, log.promptTokens - cachedTokens)
        (
            uncachedInput / 1_000_000.0 * segment.inputPrice.coerceAtLeast(0.0) +
                cachedTokens / 1_000_000.0 * segment.cachedPrice.coerceAtLeast(0.0) +
                log.completionTokens.coerceAtLeast(0) / 1_000_000.0 * segment.outputPrice.coerceAtLeast(0.0)
            ) * segment.multiplier.coerceAtLeast(0.0)
    }
    return PulseCostSummary(input, output, cached, cost)
}

fun compactPulseNumber(value: Long): String = when {
    value >= 1_000_000 -> "%.2fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.2fK".format(value / 1_000.0)
    else -> value.coerceAtLeast(0).toString()
}
