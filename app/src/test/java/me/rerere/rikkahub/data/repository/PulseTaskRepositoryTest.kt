package me.rerere.rikkahub.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import me.rerere.rikkahub.data.datastore.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class PulseTaskRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    private fun test(block: suspend (PulseTaskRepository, SettingsStore) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "pulse.preferences_pb") }, scope)
            store.awaitLoaded()
            block(PulseTaskRepository(store), store)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
    @Test fun `past and too distant once tasks are rejected with time context`() = test { repo, _ ->
        for (time in listOf(OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusYears(2))) {
            val result = runCatching { repo.create("test", "prompt", "once", time.toString(), null) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Current local time"))
        }
    }
    @Test fun `future offset timestamp retains its absolute instant`() = test { repo, store ->
        val future = OffsetDateTime.now().plusMinutes(3)
        val task = repo.create("reminder", "prompt", "once", future.toString(), null)
        assertEquals(future.toInstant().toEpochMilli(), task.dueAt)
        assertEquals(listOf(task), store.settingsFlow.value.pulse.tasks)
    }
    @Test fun `task limit applies atomically to concurrent requests`() = test { repo, store ->
        coroutineScope { (1..60).map { index -> async { runCatching { repo.create("t$index", "prompt", "heartbeat", null, null) }.isSuccess } }
            .awaitAll().also { assertEquals(50, it.count { success -> success }) } }
        assertEquals(50, store.settingsFlow.value.pulse.tasks.size)
    }
    @Test fun `cron task has a computed next occurrence`() = test { repo, _ ->
        assertTrue(repo.create("daily", "prompt", "cron", null, "0 9 * * *").nextAt > System.currentTimeMillis())
    }
    @Test fun `bad task fields do not mutate settings`() = test { repo, store ->
        assertTrue(runCatching { repo.create("", "prompt", "heartbeat", null, null) }.isFailure)
        assertTrue(runCatching { repo.create("x", "prompt", "other", null, null) }.isFailure)
        assertTrue(runCatching { repo.create("x", "prompt", "cron", null, "invalid") }.isFailure)
        assertTrue(store.settingsFlow.value.pulse.tasks.isEmpty())
    }
}
