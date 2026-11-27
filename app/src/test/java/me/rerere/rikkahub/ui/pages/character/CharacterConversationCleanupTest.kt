package me.rerere.rikkahub.ui.pages.character

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class CharacterConversationCleanupTest {
    @Test fun removesOnlySelectedOwnersHistoryBeforeRemovingTheRole() = runTest {
        val owner = Uuid.random()
        val otherRole = Uuid.random()
        val workAssistant = Uuid.random()
        val history = mutableMapOf(owner to 3, otherRole to 2, workAssistant to 4)
        val roles = mutableSetOf(owner, otherRole)
        val events = mutableListOf<String>()

        cleanupCharacterConversations(
            ownerIds = setOf(owner),
            clearSessions = { id ->
                assertEquals(owner, id)
                events += "sessions"
            },
            deletePersisted = { id ->
                assertEquals(listOf("sessions"), events)
                assertTrue(owner in roles)
                history.remove(id)
                events += "history"
            },
            onComplete = {
                assertFalse(owner in history)
                roles.remove(owner)
                events += "role"
            },
        )

        assertEquals(listOf("sessions", "history", "role"), events)
        assertEquals(mapOf(otherRole to 2, workAssistant to 4), history)
        assertEquals(setOf(otherRole), roles)
    }

    @Test fun waitsForTheLastHistoryDeletionBeforeCommitting() = runTest {
        val owners = linkedSetOf(Uuid.random(), Uuid.random())
        val enteredLast = CompletableDeferred<Unit>()
        val releaseLast = CompletableDeferred<Unit>()
        var committed = false
        val job = launch {
            cleanupCharacterConversations(
                ownerIds = owners,
                clearSessions = {},
                deletePersisted = { id ->
                    if (id == owners.last()) {
                        enteredLast.complete(Unit)
                        releaseLast.await()
                    }
                },
                onComplete = { committed = true },
            )
        }
        enteredLast.await()
        assertFalse(committed)
        assertTrue(job.isActive)

        releaseLast.complete(Unit)
        job.join()
        assertTrue(committed)
    }

    @Test fun failedSessionCleanupDoesNotDeleteHistoryOrTheRole() = runTest {
        val failure = IOException("Could not flush the live generation")
        var deleted = false
        var committed = false

        val actual = runCatching {
            cleanupCharacterConversations(
                ownerIds = setOf(Uuid.random()),
                clearSessions = { throw failure },
                deletePersisted = { deleted = true },
                onComplete = { committed = true },
            )
        }.exceptionOrNull()

        assertSame(failure, actual)
        assertFalse(deleted)
        assertFalse(committed)
    }

    @Test fun partialFailureKeepsAllPendingIdsAndDoesNotRetryByItself() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        val pending = linkedSetOf(first, second)
        var storedIds: Set<Uuid> = pending.toSet()
        val deleted = mutableListOf<Uuid>()
        var attempts = 0
        val failure = IOException("Storage unavailable")

        val actual = runCatching {
            cleanupCharacterConversations(
                ownerIds = pending,
                clearSessions = {},
                deletePersisted = { id ->
                    attempts++
                    if (id == second) throw failure
                    deleted += id
                },
                onComplete = { storedIds = storedIds - pending },
            )
        }.exceptionOrNull()
        runCurrent()

        assertSame(failure, actual)
        assertEquals(listOf(first), deleted)
        assertEquals(pending, storedIds)
        assertEquals(2, attempts)
    }

    @Test fun nextEntryRetriesRetainedIdsAndClearsThemAfterSuccess() = runTest {
        val owner = Uuid.random()
        var pending = setOf(owner)
        val attempted = pending
        var attempts = 0
        val delete: suspend (Uuid) -> Unit = {
            attempts++
            if (attempts == 1) throw IOException("Try on the next entry")
        }

        val first = runCatching {
            cleanupCharacterConversations(attempted, {}, delete) { pending = pending - attempted }
        }
        assertTrue(first.isFailure)
        assertEquals(setOf(owner), pending)
        assertEquals(1, attempts)

        cleanupCharacterConversations(attempted, {}, delete) { pending = pending - attempted }
        assertTrue(pending.isEmpty())
        assertEquals(2, attempts)
    }

    @Test fun newlyAddedPendingIdsAreNotRemovedWithTheCompletedSnapshot() = runTest {
        val owner = Uuid.random()
        val newOwner = Uuid.random()
        val snapshot = setOf(owner)
        var pending = snapshot

        cleanupCharacterConversations(
            ownerIds = snapshot,
            clearSessions = {},
            deletePersisted = { pending = pending + newOwner },
            onComplete = { pending = pending - snapshot },
        )

        assertEquals(setOf(newOwner), pending)
    }

    @Test fun cancellationDuringCleanupPropagatesWithoutClearingPendingIds() = runTest {
        val owner = Uuid.random()
        val failure = CancellationException("The page was closed")
        var committed = false

        val actual = runCatching {
            cleanupCharacterConversations(
                ownerIds = setOf(owner),
                clearSessions = {},
                deletePersisted = { throw failure },
                onComplete = { committed = true },
            )
        }.exceptionOrNull()

        assertSame(failure, actual)
        assertFalse(committed)
    }

    @Test fun cancelledContextCannotProceedFromSessionsToHistory() = runTest {
        var deleted = false
        var committed = false
        val job = launch {
            cleanupCharacterConversations(
                ownerIds = setOf(Uuid.random()),
                clearSessions = { currentCoroutineContext().cancel() },
                deletePersisted = { deleted = true },
                onComplete = { committed = true },
            )
        }
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(deleted)
        assertFalse(committed)
    }

    @Test fun cancelledContextCannotCommitAfterTheFinalDeletion() = runTest {
        var committed = false
        val job = launch {
            cleanupCharacterConversations(
                ownerIds = setOf(Uuid.random()),
                clearSessions = {},
                deletePersisted = { currentCoroutineContext().cancel() },
                onComplete = { committed = true },
            )
        }
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(committed)
    }

    @Test fun settingsWriteFailurePropagatesInsteadOfReportingCompletion() = runTest {
        val failure = IOException("Settings write failed")
        var navigated = false
        val actual = runCatching {
            cleanupCharacterConversations(setOf(Uuid.random()), {}, {}) { throw failure }
            navigated = true
        }.exceptionOrNull()

        assertSame(failure, actual)
        assertFalse(navigated)
    }

    @Test fun cleanupUsesAnOwnerSnapshotRatherThanAMutatingSet() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        val owners = linkedSetOf(first, second)
        val deleted = mutableListOf<Uuid>()

        cleanupCharacterConversations(
            ownerIds = owners,
            clearSessions = { owners.clear() },
            deletePersisted = { deleted += it },
            onComplete = {},
        )

        assertEquals(listOf(first, second), deleted)
    }
}
