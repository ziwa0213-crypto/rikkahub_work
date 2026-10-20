package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.common.android.RequestClockSnapshot

internal const val GENERATION_DEADLINE_MS = 20 * 60 * 1000L
private const val GENERATION_WATCHDOG_TICK_MS = 5_000L
private const val SUSPEND_REPORT_THRESHOLD_MS = 60_000L

internal data class GenerationDeadlineExceeded(val minutes: Int, val interruptedBySleep: Boolean)

/** A child of the existing generation Job, without replacing that Job's identity. */
internal fun CoroutineScope.launchGenerationWatchdog(
    clock: () -> RequestClockSnapshot,
    onDeadline: (GenerationDeadlineExceeded) -> Unit,
    waitForTick: suspend () -> Unit = { delay(GENERATION_WATCHDOG_TICK_MS) },
): Job {
    val generationJob = checkNotNull(coroutineContext[Job])
    val started = clock()
    return launch {
        while (isActive) {
            waitForTick()
            val now = clock()
            val elapsed = now.elapsedSince(started)
            if (elapsed < GENERATION_DEADLINE_MS) continue
            // Publish the visible error before cancelling; ChatService ignores cancellation errors.
            onDeadline(GenerationDeadlineExceeded(
                minutes = (elapsed / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                interruptedBySleep = now.suspendedSince(started) >= SUSPEND_REPORT_THRESHOLD_MS,
            ))
            generationJob.cancel(CancellationException("Generation deadline exceeded"))
            return@launch
        }
    }
}
