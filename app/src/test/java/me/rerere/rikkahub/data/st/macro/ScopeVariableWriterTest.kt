package me.rerere.rikkahub.data.st.macro

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException

class ScopeVariableWriterTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(15)

    @Test fun flushWaitsForInFlight() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = ScopeVariableWriter(app) { _, _ -> started.complete(Unit); release.await() }
        try {
            writer.offer("global", mapOf("x" to "1"))
            started.await()
            val flush = async { writer.flushAndJoin() }
            yield()
            assertFalse(flush.isCompleted)
            release.complete(Unit)
            assertTrue(flush.await().isSuccess)
        } finally { app.cancel() }
    }

    @Test fun offerUpdatesLatestVersion() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val disk = mutableMapOf<String, Map<String, String>>()
        val writer = ScopeVariableWriter(app) { id, values -> disk[id] = values }
        try {
            writer.offer("conversation:A", mapOf("x" to "1"), wake = false)
            assertEquals(1L, writer.stateSnapshot("conversation:A").latestVersion)
            assertTrue(writer.flushAndJoin().isSuccess)
            assertEquals("1", disk["conversation:A"]?.get("x"))
        } finally { app.cancel() }
    }

    @Test fun inFlightClearedAfterWrite() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = ScopeVariableWriter(app) { _, _ -> started.complete(Unit); release.await() }
        try {
            writer.offer("global", mapOf("x" to "1"))
            started.await()
            assertTrue(writer.stateSnapshot("global").inFlightVersion > 0)
            release.complete(Unit)
            assertTrue(writer.flushAndJoin().isSuccess)
            val state = writer.stateSnapshot("global")
            assertEquals(state.writtenVersion, state.inFlightVersion)
        } finally { app.cancel() }
    }

    @Test fun noRetryStormOnPersistentFailure() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        var calls = 0
        val writer = ScopeVariableWriter(app) { _, _ -> calls++; throw IOException("disk full") }
        try {
            writer.offer("global", mapOf("x" to "1"), wake = false)
            assertTrue(writer.flushAndJoin().isFailure)
            val afterFailure = calls
            repeat(50) { yield() }
            assertEquals(afterFailure, calls)
            assertNotNull(writer.stateSnapshot("global").lastError)
            assertTrue(writer.flushAndJoin().isFailure)
            assertEquals(afterFailure + 1, calls)
        } finally { app.cancel() }
    }

    @Test fun retryOnNextOffer() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        var broken = true
        var saved = ""
        val writer = ScopeVariableWriter(app) { _, values ->
            if (broken) throw IOException("disk full")
            saved = values.getValue("x")
        }
        try {
            writer.offer("global", mapOf("x" to "1"), wake = false)
            assertTrue(writer.flushAndJoin().isFailure)
            broken = false
            writer.offer("global", mapOf("x" to "2"))
            assertTrue(writer.flushAndJoin().isSuccess)
            assertEquals("2", saved)
            assertNull(writer.stateSnapshot("global").lastError)
        } finally { app.cancel() }
    }

    @Test fun crossScopeBarrierAndAlreadyWrittenFlush() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val blocked = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val disk = mutableMapOf<String, Map<String, String>>()
        val writer = ScopeVariableWriter(app) { id, values ->
            if (id == "conversation:A") { started.complete(Unit); blocked.await() }
            disk[id] = values
        }
        try {
            writer.offer("global", mapOf("x" to "old"), wake = false)
            writer.offer("conversation:A", mapOf("x" to "2"), wake = false)
            writer.offer("global", mapOf("x" to "3"), wake = false)
            val flush = async { writer.flushAndJoin() }
            started.await()
            assertEquals("3", disk["global"]?.get("x"))
            assertFalse(flush.isCompleted)
            blocked.complete(Unit)
            assertTrue(flush.await().isSuccess)
            assertTrue(writer.flushAndJoin().isSuccess)
            assertEquals("2", disk["conversation:A"]?.get("x"))
        } finally { app.cancel() }
    }

    @Test fun registrySharesLockAndPreservesReferenceNumberAndArraySemantics() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val disk = mutableMapOf<String, Map<String, String>>()
        val registry = MacroStoreRegistry(ScopeVariableWriter(app) { id, values -> disk[id] = values })
        try {
            val store = registry.store("global") { mapOf("x" to "1.5", "array" to "[\"a\"]") }
            assertSame(store, registry.store("global") { emptyMap() })
            assertEquals("2.5", store.inc("x"))
            store.add("array", "b")
            assertEquals("[\"a\",\"b\"]", store.get("array"))
            withContext(Dispatchers.Default) { List(100) { launch { store.inc("count") } }.joinAll() }
            assertEquals("100", store.get("count"))
            assertTrue(registry.flushAtTurnEnd().isSuccess)
            assertEquals("100", disk["global"]?.get("count"))
        } finally { app.cancel() }
    }

    @Test fun cancelledTurnStillFlushesAndStoppedConsumerFails() = runBlocking {
        val app = CoroutineScope(coroutineContext + SupervisorJob())
        val disk = mutableMapOf<String, Map<String, String>>()
        val writer = ScopeVariableWriter(app) { id, values -> disk[id] = values }
        val turn = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                writer.offer("global", mapOf("x" to "saved"), wake = false)
                assertTrue(writer.flushAndJoin().isSuccess)
            }
        }
        turn.cancelAndJoin()
        assertEquals("saved", disk["global"]?.get("x"))
        app.cancel()
        yield()
        writer.offer("global", mapOf("x" to "later"))
        assertTrue(writer.flushAndJoin().isFailure)
    }
}
