package me.rerere.rikkahub.data.st.macro

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-scoped single consumer. The conflated channel carries wakeups, never data or barriers. */
internal class ScopeVariableWriter(
    scope: CoroutineScope,
    private val write: suspend (String, Map<String, String>) -> Unit,
) {
    internal data class ScopeState(
        var latestVersion: Long = 0,
        var inFlightVersion: Long = 0,
        var writtenVersion: Long = 0,
        var lastError: Throwable? = null,
        var errorRevision: Long = 0,
        var failedVersion: Long = 0,
        var failedTrigger: Long = 0,
    )

    private data class Snapshot(val version: Long, val values: Map<String, String>)
    private data class Barrier(
        val targets: Map<String, Long>,
        val errorsAtRegistration: Map<String, Long>,
        val done: CompletableDeferred<Result<Unit>>,
    )

    private val lock = Any()
    private var sequence = 0L
    private var trigger = 0L
    private val latest = linkedMapOf<String, Snapshot>()
    private val states = mutableMapOf<String, ScopeState>()
    private val barriers = mutableListOf<Barrier>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var stopped: Throwable? = null

    init {
        val consumer = scope.launch {
            try {
                for (ignored in signal) drain()
            } finally {
                stop(IllegalStateException("Macro variable writer stopped"))
            }
        }
        // Also covers an already-cancelled app scope where the coroutine body never starts.
        consumer.invokeOnCompletion { cause -> stop(cause ?: IllegalStateException("Macro variable writer stopped")) }
    }

    fun offer(scopeId: String, values: Map<String, String>, wake: Boolean = true) {
        require(scopeId.isNotBlank())
        synchronized(lock) {
            val version = ++sequence
            latest[scopeId] = Snapshot(version, values.toMap())
            state(scopeId).latestVersion = version
            trigger++
        }
        if (wake) signal.trySend(Unit)
    }

    private fun state(scopeId: String) = states.getOrPut(scopeId) { ScopeState() }

    internal fun stateSnapshot(scopeId: String): ScopeState = synchronized(lock) { state(scopeId).copy() }

    private suspend fun drain() {
        val batch = synchronized(lock) {
            // A wakeup queued before a failure must not cause a retry storm. Only a later
            // offer/flush (or a newer snapshot) makes a failed snapshot eligible again.
            val ready = latest.filter { (id, snap) ->
                val s = state(id)
                s.lastError == null || snap.version > s.failedVersion || trigger > s.failedTrigger
            }
            ready.forEach { (id, snap) ->
                latest.remove(id)
                state(id).inFlightVersion = maxOf(state(id).inFlightVersion, snap.version)
            }
            ready
        }
        for ((id, snap) in batch) {
            val error = try {
                write(id, snap.values)
                null
            } catch (error: Throwable) {
                error
            }
            onWriteResult(id, snap, error)
        }
    }

    private fun onWriteResult(id: String, snap: Snapshot, error: Throwable?) = synchronized(lock) {
        val s = state(id)
        if (error == null) {
            s.writtenVersion = maxOf(s.writtenVersion, snap.version)
            s.lastError = null
        } else if (snap.version > s.writtenVersion) {
            if ((latest[id]?.version ?: 0) < snap.version) latest[id] = snap
            s.lastError = error
            s.errorRevision++
            s.failedVersion = snap.version
            s.failedTrigger = trigger
        }
        if (s.inFlightVersion == snap.version) s.inFlightVersion = s.writtenVersion
        evaluateBarriersLocked()
        // Failure does not signal itself. Pending snapshots survive until an external trigger.
    }

    private fun evaluateBarriersLocked() {
        val iterator = barriers.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            val pending = b.targets.filter { (id, target) -> state(id).writtenVersion < target }
            val failure = pending.keys.firstNotNullOfOrNull { id ->
                val s = state(id)
                s.lastError.takeIf { s.errorRevision > (b.errorsAtRegistration[id] ?: 0) }
            }
            if (failure != null || pending.isEmpty()) {
                iterator.remove()
                b.done.complete(if (failure == null) Result.success(Unit) else Result.failure(failure))
            }
        }
    }

    /** Targets and registration are captured together; taking a batch cannot hide in-flight work. */
    suspend fun flushAndJoin(scopes: Set<String>? = null): Result<Unit> = withContext(NonCancellable) {
        val done = CompletableDeferred<Result<Unit>>()
        synchronized(lock) {
            stopped?.let { return@withContext Result.failure(it) }
            val targets = states.filterKeys { scopes == null || it in scopes }
                .mapValues { (_, s) -> maxOf(s.latestVersion, s.inFlightVersion) }
            if (targets.all { (id, target) -> state(id).writtenVersion >= target }) {
                return@withContext Result.success(Unit)
            }
            barriers += Barrier(targets, targets.mapValues { (id, _) -> state(id).errorRevision }, done)
            trigger++
            evaluateBarriersLocked()
        }
        signal.trySend(Unit)
        done.await()
    }

    private fun stop(error: Throwable) = synchronized(lock) {
        if (stopped == null) stopped = error
        signal.close()
        barriers.forEach { it.done.complete(Result.failure(error)) }
        barriers.clear()
    }
}
