package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import kotlin.uuid.Uuid

class ProviderListCompatibilityTest {
    @get:Rule val temp = TemporaryFolder()
    @get:Rule val timeout: Timeout = Timeout.seconds(30)

    private val retiredId = Uuid.parse("00000000-0000-0000-0000-000000000090")
    private val cloud = ProviderSetting.OpenAI(
        id = Uuid.parse("00000000-0000-0000-0000-000000000091"),
        apiKey = "test-key-do-not-log",
        baseUrl = "https://example.invalid/v1",
        models = listOf(Model(modelId = "cloud-test")),
        customHeaders = listOf(CustomHeader("X-Test", "kept")),
        includeHistoryReasoning = false,
    )
    private val legacyProviders = JsonArray(listOf(
        buildJsonObject {
            put("type", "local_llamacpp")
            put("id", retiredId.toString())
            // Even obsolete or malformed internals must never reach its former serializer.
            put("models", JsonPrimitive("obsolete"))
        },
        JsonInstant.encodeToJsonElement<ProviderSetting>(cloud),
    ))

    @Test fun `backup filters retired provider and preserves cloud configuration`() {
        val raw = buildJsonObject {
            put("providers", legacyProviders)
            put("titlePrompt", "keep custom prompt")
            put("enableMemoryFusion", true)
            put("enableLocalGpu", true)
        }.toString()
        val restored = JsonInstant.decodeFromString<Settings>(raw)
        assertEquals(listOf(cloud), restored.providers)
        assertEquals("keep custom prompt", restored.titlePrompt)
        val encoded = JsonInstant.encodeToString(restored)
        assertFalse(encoded.contains("local_llamacpp"))
        assertFalse(encoded.contains("enableMemoryFusion"))
        assertFalse(encoded.contains("enableLocalGpu"))
        assertEquals(restored, JsonInstant.decodeFromString<Settings>(encoded))
    }

    @Test fun `real datastore starts with legacy providers and keeps credentials on restart`() = runBlocking {
        val file = File(temp.root, "legacy.preferences_pb")
        val oldFusion = booleanPreferencesKey("enable_memory_fusion")
        val oldGpu = booleanPreferencesKey("enable_local_gpu")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val disk = PreferenceDataStoreFactory.create(scope = scope) { file }
            disk.edit {
                it.putSettings(Settings(
                    providers = listOf(cloud),
                    chatModelId = retiredId,
                    fastModelId = retiredId,
                    translateModeId = retiredId,
                    ocrModelId = retiredId,
                    compressModelId = retiredId,
                    imageGenerationModelId = retiredId,
                    assistants = listOf(Assistant(name = "legacy assistant", chatModelId = retiredId)),
                    groupMembers = listOf(GroupMember(name = "legacy member", chatModelId = retiredId)),
                    pulse = PulseConfig(chatModelId = retiredId),
                    favoriteModels = listOf(retiredId, cloud.models.single().id),
                ))
                it[SettingsStore.PROVIDERS] = legacyProviders.toString()
                it[SettingsStore.TITLE_PROMPT] = "kept on disk"
                it[oldFusion] = true
                it[oldGpu] = true
            }
            val store = SettingsStore(disk, scope)
            val loaded = store.awaitLoaded()
            assertFalse(loaded.providers.any { it.id == retiredId })
            assertEquals(cloud, loaded.providers.single { it.id == cloud.id })
            assertEquals(cloud.models.single().id, loaded.chatModelId)
            assertEquals(listOf(retiredId, retiredId, retiredId, retiredId, retiredId), listOf(
                loaded.fastModelId, loaded.translateModeId, loaded.ocrModelId,
                loaded.compressModelId, loaded.imageGenerationModelId,
            ))
            assertEquals(null, loaded.assistants.single { it.name == "legacy assistant" }.chatModelId)
            assertEquals(null, loaded.groupMembers.single().chatModelId)
            assertEquals(null, loaded.pulse.chatModelId)
            assertEquals(listOf(cloud.models.single().id), loaded.favoriteModels)
            assertEquals("kept on disk", loaded.titlePrompt)
            store.update { it.copy(titlePrompt = "saved after migration") }
            assertFalse(disk.data.first()[SettingsStore.PROVIDERS]!!.contains(retiredId.toString()))
            assertTrue(disk.data.first()[oldFusion] == true)
            assertTrue(disk.data.first()[oldGpu] == true)
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file }, scope).awaitLoaded()
            assertEquals(cloud, reopened.providers.single { it.id == cloud.id })
            assertEquals(cloud.models.single().id, reopened.chatModelId)
            assertEquals(null, reopened.groupMembers.single().chatModelId)
            assertEquals("saved after migration", reopened.titlePrompt)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test fun `unknown and malformed providers are not silently discarded`() {
        listOf("""[{"type":"unknown-provider"}]""", """[{"type":{}}]""", """{}""").forEach { raw ->
            assertThrows(SerializationException::class.java) {
                JsonInstant.decodeFromString(ProviderListCompatSerializer, raw)
            }
        }
    }

    @Test fun `valid selections survive normalization`() {
        val id = cloud.models.single().id
        val settings = Settings(
            providers = listOf(cloud), chatModelId = id,
            assistants = listOf(Assistant(chatModelId = id)),
            groupMembers = listOf(GroupMember(chatModelId = id)),
            pulse = PulseConfig(chatModelId = id),
        ).normalized()
        assertEquals(id, settings.chatModelId)
        assertEquals(id, settings.assistants.first().chatModelId)
        assertEquals(id, settings.groupMembers.single().chatModelId)
        assertEquals(id, settings.pulse.chatModelId)
    }

    @Test fun `no remaining model preserves nonnull global selection without inventing one`() {
        val settings = Settings(providers = listOf(cloud.copy(models = emptyList())), chatModelId = retiredId)
            .withoutInvalidReferences()
        assertEquals(retiredId, settings.chatModelId)
        assertTrue(settings.providers.all { it.models.isEmpty() })
    }
}
