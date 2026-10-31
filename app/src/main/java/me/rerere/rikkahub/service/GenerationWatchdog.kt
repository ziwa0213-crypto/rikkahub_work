package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import me.rerere.common.android.RequestClockSnapshot
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal const val GENERATION_DEADLINE_MS = 20 * 60 * 1000L
private const val GENERATION_WATCHDOG_TICK_MS = 5_000L
private const val SUSPEND_REPORT_THRESHOLD_MS = 60_000L

internal data class GenerationDeadlineExceeded(val minutes: Int, val interruptedBySleep: Boolean)

/** Real stream/tool progress only; UI ticks and retry notifications must not extend the deadline. */
internal class GenerationActivity(private val clock: () -> RequestClockSnapshot) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GenerationActivity>

    @Volatile
    var lastActivity: RequestClockSnapshot = clock()
        private set

    fun touch() { lastActivity = clock() }
}

internal suspend fun markGenerationProgress() {
    currentCoroutineContext()[GenerationActivity]?.touch()
}

/** A child of the existing generation Job, without replacing that Job's identity. */
internal fun CoroutineScope.launchGenerationWatchdog(
    clock: () -> RequestClockSnapshot,
    onDeadline: (GenerationDeadlineExceeded) -> Unit,
    waitForTick: suspend () -> Unit = { delay(GENERATION_WATCHDOG_TICK_MS) },
    lastActivity: () -> RequestClockSnapshot? = { null },
): Job {
    val generationJob = checkNotNull(coroutineContext[Job])
    val started = clock()
    return launch {
        while (isActive) {
            waitForTick()
            val now = clock()
            val lastProgress = lastActivity() ?: started
            val elapsed = now.elapsedSince(lastProgress)
            if (elapsed < GENERATION_DEADLINE_MS) continue
            // Publish the visible error before cancelling; ChatService ignores cancellation errors.
            onDeadline(GenerationDeadlineExceeded(
                minutes = (elapsed / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                interruptedBySleep = now.suspendedSince(lastProgress) >= SUSPEND_REPORT_THRESHOLD_MS,
            ))
            generationJob.cancel(CancellationException("Generation deadline exceeded"))
            return@launch
        }
    }
}
