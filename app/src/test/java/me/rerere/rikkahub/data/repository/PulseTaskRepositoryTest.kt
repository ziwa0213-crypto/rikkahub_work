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

    @Test fun `partial update keeps execution history and rescheduling resets slot`() = test { repo, store ->
        val task = repo.create("daily", "original", "cron", null, "0 9 * * *")
        store.update { it.copy(pulse = it.pulse.copy(tasks = listOf(task.copy(
            lastRunAt = 123, lastResult = "REPORTED", lastSlot = Long.MAX_VALUE)))) }
        val paused = repo.update(task.id, enabled = false)
        assertFalse(paused.enabled)
        assertEquals(Long.MAX_VALUE, paused.lastSlot)
        assertEquals(task.nextAt, paused.nextAt)
        val updated = repo.update(task.id, title = "new title", cron = "0 8 * * *", enabled = true)
        assertEquals(0L, updated.lastSlot)
        assertEquals("0 8 * * *", updated.cron)
        assertTrue(updated.nextAt > System.currentTimeMillis())
        assertEquals(123L, updated.lastRunAt)
        assertEquals("REPORTED", updated.lastResult)
        assertEquals(task.createdAt, updated.createdAt)
        assertEquals(task.timeZoneId, updated.timeZoneId)
        assertEquals(task.prompt, updated.prompt)
    }

    @Test fun `list update and delete remain available at task limit and missing ids are explicit`() = test { repo, _ ->
        repeat(50) { repo.create("t$it", "prompt", "heartbeat", null, null) }
        assertEquals(50, repo.list().size)
        assertTrue(runCatching { repo.create("overflow", "prompt", "heartbeat", null, null) }.isFailure)
        val id = repo.list().first().id
        repo.update(id, prompt = "changed")
        assertEquals("changed", repo.list().first().prompt)
        assertTrue(repo.delete(id))
        assertFalse(repo.delete(id))
        val error = runCatching { repo.update(id, enabled = true) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("action=list"))
        assertEquals(49, repo.list().size)
    }

    @Test fun `concurrent partial updates preserve each others fields`() = test { repo, _ ->
        val task = repo.create("title", "prompt", "heartbeat", null, null)
        coroutineScope {
            listOf(async { repo.update(task.id, title = "renamed") }, async { repo.update(task.id, prompt = "new prompt") }).awaitAll()
        }
        val latest = repo.list().single()
        assertEquals("renamed", latest.title)
        assertEquals("new prompt", latest.prompt)
    }

    @Test fun `tool preserves old create arguments and lists only bounded prompt preview`() = test { repo, _ ->
        val tool = me.rerere.rikkahub.data.ai.tools.pulse.pulseTaskTool(repo)
        tool.execute(kotlinx.serialization.json.buildJsonObject {
            put("title", kotlinx.serialization.json.JsonPrimitive("old call"))
            put("prompt", kotlinx.serialization.json.JsonPrimitive("x".repeat(300)))
            put("when", kotlinx.serialization.json.JsonPrimitive("heartbeat"))
        })
        val result = tool.execute(kotlinx.serialization.json.buildJsonObject { put("action", kotlinx.serialization.json.JsonPrimitive("list")) })
            .filterIsInstance<me.rerere.ai.ui.UIMessagePart.Text>().single().text
        val item = me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(result) as kotlinx.serialization.json.JsonArray
        val json = item.single() as kotlinx.serialization.json.JsonObject
        assertEquals(200, (json["prompt"] as kotlinx.serialization.json.JsonPrimitive).content.length)
        assertTrue(json.containsKey("next_at_local"))
        assertEquals(300, repo.list().single().prompt.length)
    }
}
