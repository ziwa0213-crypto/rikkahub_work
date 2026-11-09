package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationWakeLockRenewalTest {
    private class Scheduler {
        data class Entry(val callback: Runnable, val at: Long)

        var now = 0L
        val entries = mutableListOf<Entry>()

        fun post(callback: Runnable, delayMs: Long) {
            entries += Entry(callback, now + delayMs)
        }

        fun remove(callback: Runnable) {
            entries.removeAll { it.callback === callback }
        }

        fun advanceBy(ms: Long) {
            val target = now + ms
            while (true) {
                val next = entries.minByOrNull { it.at } ?: break
                if (next.at > target) break
                entries.remove(next)
                now = next.at
                next.callback.run()
            }
            now = target
        }
    }

    @Test
    fun renewsEveryMinuteBeyondTheFormerTwentyMinuteAndTwoHourLimits() {
        val scheduler = Scheduler()
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ true }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()

        assertEquals(1, renewals)
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS - 1)
        assertEquals(1, renewals)
        scheduler.advanceBy(1)
        assertEquals(2, renewals)
        scheduler.advanceBy(179 * GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(181, renewals)
        assertEquals(1, scheduler.entries.size)
    }

    @Test
    fun inactiveServiceNeverStartsRenewal() {
        val scheduler = Scheduler()
        val renewal = GenerationWakeLockRenewal({ false }, { error("Must not renew") }, scheduler::post, scheduler::remove)
        renewal.start()
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun repeatedStartKeepsTheSameRenewalDeadline() {
        val scheduler = Scheduler()
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ true }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()
        val first = scheduler.entries.single()
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS - 1)
        renewal.start()
        assertEquals(1, renewals)
        assertSame(first, scheduler.entries.single())
        scheduler.advanceBy(1)
        assertEquals(2, renewals)
        assertEquals(1, scheduler.entries.size)
    }

    @Test
    fun stoppingRemovesTheCallbackAndLateDeliveryCannotRestartIt() {
        val scheduler = Scheduler()
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ true }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()
        val stale = scheduler.entries.single().callback
        renewal.stop()
        renewal.stop()
        stale.run()
        assertEquals(1, renewals)
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun losingTheLastGenerationStopsTheLoop() {
        val scheduler = Scheduler()
        var active = true
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ active }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()
        active = false
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(1, renewals)
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun stoppingDuringRenewalDoesNotScheduleAgain() {
        val scheduler = Scheduler()
        lateinit var renewal: GenerationWakeLockRenewal
        renewal = GenerationWakeLockRenewal({ true }, { renewal.stop() }, scheduler::post, scheduler::remove)
        renewal.start()
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun restartingDuringRenewalDoesNotDuplicateTheNewLoop() {
        val scheduler = Scheduler()
        lateinit var renewal: GenerationWakeLockRenewal
        renewal = GenerationWakeLockRenewal({ true }, { renewal.start() }, scheduler::post, scheduler::remove)
        renewal.start()
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(1, scheduler.entries.size)
    }

    @Test
    fun inactiveUpdateRemovesAnAlreadyScheduledRenewal() {
        val scheduler = Scheduler()
        var active = true
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ active }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()
        val stale = scheduler.entries.single().callback

        active = false
        renewal.start()
        stale.run()
        assertEquals(1, renewals)
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun restartingAfterIdleAcquiresImmediatelyAndRejectsTheOldCallback() {
        val scheduler = Scheduler()
        var renewals = 0
        val renewal = GenerationWakeLockRenewal({ true }, { renewals++ }, scheduler::post, scheduler::remove)
        renewal.start()
        val stale = scheduler.entries.single().callback
        renewal.stop()

        scheduler.advanceBy(10 * GENERATION_WAKE_LOCK_LEASE_MS)
        renewal.start()
        assertEquals(2, renewals)
        stale.run()
        assertEquals(2, renewals)
        assertEquals(1, scheduler.entries.size)
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(3, renewals)
    }

    @Test
    fun frequentTaskUpdatesCannotStarveTheSharedLease() {
        val scheduler = Scheduler()
        var activeGenerations = 2
        var renewals = 0
        val renewal = GenerationWakeLockRenewal(
            { activeGenerations > 0 },
            { renewals++ },
            scheduler::post,
            scheduler::remove,
        )
        renewal.start()
        repeat(36) {
            scheduler.advanceBy(10_000)
            activeGenerations = if (activeGenerations == 1) 2 else 1
            renewal.start()
        }
        assertEquals(7, renewals)
        assertEquals(1, scheduler.entries.size)

        activeGenerations = 0
        renewal.stop()
        scheduler.advanceBy(10 * GENERATION_WAKE_LOCK_LEASE_MS)
        assertEquals(7, renewals)
        assertTrue(scheduler.entries.isEmpty())
    }

    @Test
    fun stoppingThenRestartingInsideRenewalLeavesOnlyTheNewLoop() {
        val scheduler = Scheduler()
        var renewals = 0
        lateinit var renewal: GenerationWakeLockRenewal
        renewal = GenerationWakeLockRenewal(
            { true },
            {
                renewals++
                if (renewals == 2) {
                    renewal.stop()
                    renewal.start()
                }
            },
            scheduler::post,
            scheduler::remove,
        )
        renewal.start()
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(3, renewals)
        assertEquals(1, scheduler.entries.size)
        scheduler.advanceBy(GENERATION_WAKE_LOCK_RENEW_MS)
        assertEquals(4, renewals)
        assertEquals(1, scheduler.entries.size)
    }
}
