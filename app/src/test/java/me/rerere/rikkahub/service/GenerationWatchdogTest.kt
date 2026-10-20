package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rerere.common.android.RequestClockSnapshot
import org.junit.Assert.*
import org.junit.Test

class GenerationWatchdogTest {
    @Test fun timeoutPublishesErrorBeforeCancellingOriginalJob() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        var now = RequestClockSnapshot(1000, 500)
        val errors = mutableListOf<GenerationDeadlineExceeded>()
        val released = CompletableDeferred<Unit>()
        val generation = launch(start = CoroutineStart.UNDISPATCHED) {
            val originalJob = coroutineContext[Job]!!
            val watchdog = launchGenerationWatchdog(
                clock = { now },
                onDeadline = {
                    assertTrue(originalJob.isActive)
                    errors.add(it)
                },
                waitForTick = { ticks.receive() },
            )
            assertSame(originalJob, coroutineContext[Job])
            try { awaitCancellation() } finally { watchdog.cancel(); released.complete(Unit) }
        }
        yield()
        now = RequestClockSnapshot(1000 + GENERATION_DEADLINE_MS - 1, 500 + GENERATION_DEADLINE_MS - 1)
        ticks.send(Unit)
        yield()
        assertTrue(generation.isActive)
        assertTrue(errors.isEmpty())
        now = RequestClockSnapshot(1000 + GENERATION_DEADLINE_MS, 500 + GENERATION_DEADLINE_MS)
        ticks.send(Unit)
        generation.join()
        assertTrue(generation.isCancelled)
        assertTrue(released.isCompleted)
        assertEquals(listOf(GenerationDeadlineExceeded(20, false)), errors)
    }

    @Test fun firstTickAfterTwoHoursOfSleepImmediatelyCancels() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        var now = RequestClockSnapshot(10_000, 9000)
        var result: GenerationDeadlineExceeded? = null
        val generation = launch {
            val watchdog = launchGenerationWatchdog({ now }, { result = it }, { ticks.receive() })
            try { awaitCancellation() } finally { watchdog.cancel() }
        }
        yield()
        now = RequestClockSnapshot(10_000 + 7_230_000, 9000 + 30_000)
        ticks.send(Unit)
        generation.join()
        assertEquals(GenerationDeadlineExceeded(120, true), result)
    }

    @Test fun normalFinishAndManualCancelDoNotLeaveWatchdogOrReportError() = runBlocking {
        for (cancel in listOf(false, true)) {
            val done = CompletableDeferred<Unit>()
            var watchdog: Job? = null
            val generation = launch {
                watchdog = launchGenerationWatchdog({ RequestClockSnapshot(0, 0) }, { fail("Unexpected deadline") })
                try { done.await() } finally { watchdog!!.cancel() }
            }
            yield()
            if (cancel) generation.cancel() else done.complete(Unit)
            generation.join()
            assertTrue(watchdog!!.isCompleted)
            assertEquals(cancel, generation.isCancelled)
        }
    }

    @Test fun simultaneousGenerationsHaveIndependentDeadlinesAndCancellation() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        var now = RequestClockSnapshot(0, 0)
        val other = launch { awaitCancellation() }
        val first = launch {
            val watchdog = launchGenerationWatchdog({ now }, {}, { ticks.receive() })
            try { awaitCancellation() } finally { watchdog.cancel() }
        }
        yield()
        now = RequestClockSnapshot(GENERATION_DEADLINE_MS, GENERATION_DEADLINE_MS)
        ticks.send(Unit)
        first.join()
        assertTrue(other.isActive)
        other.cancel()
        other.join()
    }
}
