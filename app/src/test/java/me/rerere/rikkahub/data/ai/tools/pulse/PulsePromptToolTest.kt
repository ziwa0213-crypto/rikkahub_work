package me.rerere.rikkahub.data.ai.tools.pulse

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DEFAULT_HEARTBEAT_PROMPT
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PulsePromptToolTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `read returns the latest effective heartbeat verbatim without changing settings`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "read.preferences_pb") }, scope)
            store.awaitLoaded()
            store.update { it.copy(pulse = it.pulse.copy(systemPrompt = "private persona")) }
            val tool = updateHeartbeatPromptTool(store)
            assertEquals(emptyList<String>(), (tool.parameters() as InputSchema.Obj).required)
            val args = buildJsonObject { put("action", "read") }
            assertFalse(tool.needsApproval(args))

            // Reuse one registered tool across manual saves, including text longer than the write limit.
            for (stored in listOf("  检查待办\nPreserve spacing.  ", "newly confirmed text", "", " \n\t", DEFAULT_HEARTBEAT_PROMPT, "x".repeat(4001))) {
                store.update { it.copy(pulse = it.pulse.copy(heartbeatPrompt = stored)) }
                val before = store.settingsFlow.value
                val result = Json.parseToJsonElement((tool.execute(args).single() as UIMessagePart.Text).text).jsonObject
                val expected = stored.ifBlank { DEFAULT_HEARTBEAT_PROMPT }
                assertEquals(setOf("content", "is_default", "length"), result.keys)
                assertEquals(expected, result.getValue("content").jsonPrimitive.content)
                assertEquals(stored.isBlank(), result.getValue("is_default").jsonPrimitive.boolean)
                assertEquals(expected.length, result.getValue("length").jsonPrimitive.int)
                assertEquals(before, store.settingsFlow.value)
            }
            store.update { it.copy(pulse = it.pulse.copy(enableSelfPromptEdit = false)) }
            val before = store.settingsFlow.value
            assertTrue(runCatching { tool.execute(args) }.isFailure)
            assertEquals(before, store.settingsFlow.value)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun `read edit and rewrite round trips preserve persona and write validation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "roundtrip.preferences_pb") }, scope)
            store.awaitLoaded()
            store.update { it.copy(pulse = it.pulse.copy(systemPrompt = "persona", heartbeatPrompt = "original")) }
            val tool = updateHeartbeatPromptTool(store)
            val read = buildJsonObject { put("action", "read") }
            suspend fun content() = Json.parseToJsonElement((tool.execute(read).single() as UIMessagePart.Text).text)
                .jsonObject.getValue("content").jsonPrimitive.content
            assertEquals("original", content())
            assertTrue(runCatching { tool.execute(buildJsonObject {}) }.isFailure)
            assertTrue(runCatching { tool.execute(buildJsonObject { put("action", "edit") }) }.isFailure)
            tool.execute(buildJsonObject { put("prompt", "  edited  ") })
            assertEquals("edited", content())
            val rewrite = buildJsonObject { put("action", "rewrite"); put("prompt", "rewritten") }
            assertTrue(runCatching { tool.execute(rewrite) }.isFailure)
            assertEquals("edited", content())
            store.update { it.copy(pulse = it.pulse.copy(enableSelfPromptRewrite = true)) }
            tool.execute(rewrite)
            assertEquals("rewritten", content())
            assertEquals("persona", store.settingsFlow.value.pulse.systemPrompt)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

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
