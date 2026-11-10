package me.rerere.rikkahub.data.pulse

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.PulseConfig
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid

@Serializable
enum class PulsePriceKind { PEAK, OFF }

/** A weekly half-open price window. An empty [days] list means every day. */
@Serializable
data class PulsePriceSegment(
    val id: String = Uuid.random().toString(),
    val kind: PulsePriceKind = PulsePriceKind.PEAK,
    val startMinute: Int = 540,
    val endMinute: Int = 720,
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val inputPrice: Double = 0.0,
    val outputPrice: Double = 0.0,
    val cachedPrice: Double = 0.0,
    val multiplier: Double = 1.0,
)

fun PulseConfig.priceSegmentOf(at: Long, zoneId: ZoneId = ZoneId.systemDefault()): PulsePriceSegment? {
    val local = Instant.ofEpochMilli(at).atZone(zoneId)
    val minute = local.hour * 60 + local.minute
    return priceSegments.lastOrNull { it.matches(local.dayOfWeek.value, minute) }
}

fun PulsePriceSegment.matches(dayOfWeek: Int, minute: Int): Boolean {
    if (dayOfWeek !in 1..7 || minute !in 0 until MINUTES_PER_DAY) return false
    val start = startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    val end = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    return when {
        start == end -> days.isEmpty() || dayOfWeek in days
        start < end -> (days.isEmpty() || dayOfWeek in days) && minute >= start && minute < end
        else -> (days.isEmpty() || dayOfWeek in days) && (minute >= start || minute < end)
    }
}

fun pulsePriceSegmentsOverlap(first: PulsePriceSegment, second: PulsePriceSegment): Boolean {
    val firstDays = first.days.ifEmpty { (1..7).toList() }.toSet()
    val secondDays = second.days.ifEmpty { (1..7).toList() }.toSet()
    if (firstDays.intersect(secondDays).isEmpty()) return false
    val firstRanges = first.minuteRanges()
    val secondRanges = second.minuteRanges()
    return firstRanges.any { a -> secondRanges.any { b -> a.first < b.second && b.first < a.second } }
}

fun pulsePriceSegmentConflict(
    candidate: PulsePriceSegment,
    existing: List<PulsePriceSegment>,
): PulsePriceSegment? = existing.lastOrNull { it.id != candidate.id && pulsePriceSegmentsOverlap(candidate, it) }

/** Number of uncovered 15-minute sample slots in one week. */
fun uncoveredPulseQuarterHours(segments: List<PulsePriceSegment>): Int =
    (1..7).sumOf { day ->
        (0 until QUARTERS_PER_DAY).count { quarter ->
            val minute = quarter * MINUTES_PER_QUARTER
            segments.none { it.matches(day, minute) }
        }
    }

private fun PulsePriceSegment.minuteRanges(): List<Pair<Int, Int>> {
    val start = startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    val end = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    return when {
        start == end -> listOf(0 to MINUTES_PER_DAY)
        start < end -> listOf(start to end)
        else -> listOf(start to MINUTES_PER_DAY, 0 to end)
    }
}

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_QUARTER = 15
private const val QUARTERS_PER_DAY = MINUTES_PER_DAY / MINUTES_PER_QUARTER
