package me.rerere.rikkahub.service

internal const val GENERATION_WAKE_LOCK_LEASE_MS = 5 * 60 * 1000L
internal const val GENERATION_WAKE_LOCK_RENEW_MS = 60 * 1000L

/** Main-thread renewal only: this never measures or limits a generation's duration. */
internal class GenerationWakeLockRenewal(
    private val isActive: () -> Boolean,
    private val renew: () -> Unit,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
) {
    private var pending: Runnable? = null

    fun start() {
        if (!isActive()) {
            stop()
            return
        }
        // Concurrent generations share the lease. Repeated updates must not postpone renewal.
        if (pending != null) return

        val callback = object : Runnable {
            override fun run() {
                // A removed callback must not revive a stopped or restarted lease.
                if (pending !== this) return
                if (!isActive()) {
                    stop()
                    return
                }
                renew()
                if (pending !== this) return
                if (isActive()) postDelayed(this, GENERATION_WAKE_LOCK_RENEW_MS) else stop()
            }
        }
        pending = callback
        // Acquire before waiting for the first renewal, including after an idle period.
        callback.run()
    }

    fun stop() {
        val callback = pending ?: return
        pending = null
        removeCallbacks(callback)
    }
}
