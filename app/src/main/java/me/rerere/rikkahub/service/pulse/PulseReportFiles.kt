package me.rerere.rikkahub.service.pulse

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault())
private val REPORT_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}-\\d{4}-(ok|partial|failed)(?:-(\\d+))?\\.txt$")

internal fun pulseReportFileName(at: Long, result: String, sequence: Int = 1): String {
    val status = when (result) {
        "FAILED" -> "failed"
        "PARTIAL", "REPORTED" -> "partial"
        else -> "ok"
    }
    val suffix = if (sequence <= 1) "" else "-$sequence"
    return "${REPORT_TIME.format(Instant.ofEpochMilli(at))}-$status$suffix.txt"
}

internal fun isPulseReportFileName(name: String): Boolean = REPORT_PATTERN.matches(name)

internal fun pulseReportSortKey(name: String): String? = name.takeIf(::isPulseReportFileName)?.let {
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2}-\\d{4})-(?:ok|partial|failed)(?:-(\\d+))?\\.txt$").matchEntire(it)
        ?: return@let null
    val timestamp = match.groupValues[1]
    val sequence = match.groupValues[2].toIntOrNull() ?: 1
    "$timestamp-${String.format(Locale.ROOT, "%010d", sequence)}"
}

internal fun pulseReportRetention(files: List<String>, keep: Int = 5): List<String> =
    files.filter(::isPulseReportFileName).sortedBy(::pulseReportSortKey)
        .take((files.count(::isPulseReportFileName) - keep.coerceAtLeast(0)).coerceAtLeast(0))
