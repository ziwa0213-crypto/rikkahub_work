package me.rerere.rikkahub.data.ai.tools.pulse

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PulsePromptToolTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `only heartbeat text changes and current switches override stale registration`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "prompt.preferences_pb") }, scope)
            store.awaitLoaded()
            store.update { it.copy(pulse = it.pulse.copy(systemPrompt = "persona")) }
            val tool = updateHeartbeatPromptTool(store)
            val args = buildJsonObject { put("prompt", "  check tasks  ") }
            assertFalse(tool.needsApproval(args))
            tool.execute(args)
            assertEquals("check tasks", store.settingsFlow.value.pulse.heartbeatPrompt)
            assertEquals("persona", store.settingsFlow.value.pulse.systemPrompt)
            val initial = store.settingsFlow.value.pulse
            for (invalid in listOf("", " ", "x".repeat(4001))) {
                assertTrue(runCatching { tool.execute(buildJsonObject { put("prompt", invalid) }) }.isFailure)
            }
            assertTrue(runCatching { tool.execute(buildJsonObject { put("prompt", "rewrite"); put("action", "rewrite") }) }.isFailure)
            assertEquals(initial, store.settingsFlow.value.pulse)
            store.update { it.copy(pulse = it.pulse.copy(enableSelfPromptRewrite = true)) }
            tool.execute(buildJsonObject { put("prompt", "rewritten"); put("action", "rewrite") })
            store.update { it.copy(pulse = it.pulse.copy(enableSelfPromptEdit = false)) }
            assertTrue(runCatching { tool.execute(args) }.isFailure)
            assertTrue(store.settingsFlow.value.pulse.enableSelfPromptRewrite)
            assertEquals("rewritten", store.settingsFlow.value.pulse.heartbeatPrompt)
            assertEquals("persona", store.settingsFlow.value.pulse.systemPrompt)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
