package me.rerere.common.android

/** Monotonic clock samples. Elapsed includes device suspend; uptime excludes it. */
data class RequestClockSnapshot(val elapsedMs: Long, val uptimeMs: Long) {
    fun elapsedSince(start: RequestClockSnapshot): Long = (elapsedMs - start.elapsedMs).coerceAtLeast(0)
    fun awakeSince(start: RequestClockSnapshot): Long = (uptimeMs - start.uptimeMs).coerceAtLeast(0)
    fun suspendedSince(start: RequestClockSnapshot): Long = (elapsedSince(start) - awakeSince(start)).coerceAtLeast(0)
}
