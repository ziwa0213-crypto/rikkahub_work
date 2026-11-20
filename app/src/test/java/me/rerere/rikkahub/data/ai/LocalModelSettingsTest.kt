package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.InstalledLocalModel
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupMember
import org.junit.Assert.*
import org.junit.Test

class LocalModelSettingsTest {
    @Test fun `repair preserves user chosen modalities when projector remains present`() {
        val model = Model(modelId = "custom.gguf", inputModalities = listOf(Modality.TEXT, Modality.VIDEO))
        val settings = Settings(providers = listOf(ProviderSetting.LlamaCppLocal(models = listOf(model))))
        val installed = InstalledLocalModel(model.modelId, "/private/custom.gguf", "/private/mmproj.gguf")
        val next = settings.withLocalModels(listOf(installed))
        assertEquals(model, next.providers.single().models.single())
        val textOnly = model.copy(inputModalities = listOf(Modality.TEXT))
        val disabled = settings.copy(providers = listOf(ProviderSetting.LlamaCppLocal(models = listOf(textOnly))))
        assertEquals(textOnly, disabled.withLocalModels(listOf(installed)).providers.single().models.single())
        assertTrue(disabled.withLocalModels(listOf(installed.copy(projectorAdded = true)))
            .providers.single().models.single().inputModalities.contains(Modality.VIDEO))
        assertEquals(listOf(Modality.TEXT), next.withLocalModels(listOf(installed.copy(projector = null)))
            .providers.single().models.single().inputModalities)
    }
    @Test fun `repair retains installed UUID and cleans deleted model selections without touching API provider`() {
        val api = ProviderSetting.OpenAI(apiKey = "retained", models = listOf(Model(modelId = "remote")))
        val keep = Model(modelId = "keep.gguf")
        val gone = Model(modelId = "gone.gguf")
        val local = ProviderSetting.LlamaCppLocal(models = listOf(keep, gone))
        val settings = Settings(providers = listOf(api, local), chatModelId = gone.id, fastModelId = gone.id,
            favoriteModels = listOf(keep.id, gone.id), assistants = listOf(Assistant(chatModelId = gone.id)),
            groupMembers = listOf(GroupMember(chatModelId = gone.id)))
        val next = settings.withLocalModels(listOf(InstalledLocalModel(keep.modelId, "/private/keep.gguf", null)))
        assertSame(api, next.providers.first())
        assertEquals(keep.contextLength, next.providers.last().models.single().contextLength)
        assertEquals(keep.id, next.providers.last().models.single().id)
        assertEquals(listOf(keep.id), next.favoriteModels)
        assertEquals(api.models.single().id, next.chatModelId)
        assertNull(next.assistants.single().chatModelId)
        assertNull(next.groupMembers.single().chatModelId)
    }

    @Test fun `empty settings gain exactly one local builtin and newly imported model`() {
        val initial = Settings(providers = emptyList())
        val once = initial.withLocalModels(listOf(InstalledLocalModel("new.gguf", "/private/new.gguf", null)))
        val twice = once.withLocalModels(listOf(InstalledLocalModel("new.gguf", "/private/new.gguf", null)))
        assertEquals(1, twice.providers.size)
        assertEquals(8192, once.providers.single().models.single().contextLength)
        assertEquals(once.providers.single().models.single().id, twice.providers.single().models.single().id)
    }

    @Test fun `reconciliation preserves stored 16K and explicitly chosen 4K contexts`() {
        for (window in listOf(4096, 16384)) {
            val model = Model(modelId = "old.gguf", contextLength = window)
            val settings = Settings(providers = listOf(ProviderSetting.LlamaCppLocal(models = listOf(model))))
            val next = settings.withLocalModels(listOf(InstalledLocalModel(model.modelId, "/private/old.gguf", null)))
            assertEquals(model, next.providers.single().models.single())
        }
    }

    @Test fun `migration moves all global slots to the first cloud model and clears scoped references`() {
        val cloudModel = Model(modelId = "cloud")
        val localModel = Model(modelId = "local.gguf")
        val cloud = ProviderSetting.OpenAI(apiKey = "retained", models = listOf(cloudModel))
        val local = ProviderSetting.LlamaCppLocal(models = listOf(localModel))
        val settings = Settings(
            providers = listOf(local, cloud),
            chatModelId = localModel.id,
            fastModelId = localModel.id,
            memoryModelId = localModel.id,
            translateModeId = localModel.id,
            ocrModelId = localModel.id,
            compressModelId = localModel.id,
            imageGenerationModelId = localModel.id,
            assistants = listOf(Assistant(chatModelId = localModel.id)),
            groupMembers = listOf(GroupMember(chatModelId = localModel.id)),
            pulse = PulseConfig(chatModelId = localModel.id),
        )

        val migrated = settings.migrateAwayFromLocalModels()

        assertEquals(cloudModel.id, migrated.chatModelId)
        assertEquals(cloudModel.id, migrated.fastModelId)
        assertNull(migrated.memoryModelId)
        assertEquals(cloudModel.id, migrated.translateModeId)
        assertEquals(cloudModel.id, migrated.ocrModelId)
        assertEquals(cloudModel.id, migrated.compressModelId)
        assertEquals(cloudModel.id, migrated.imageGenerationModelId)
        assertNull(migrated.assistants.single().chatModelId)
        assertNull(migrated.groupMembers.single().chatModelId)
        assertNull(migrated.pulse.chatModelId)
        assertEquals(settings.providers, migrated.providers)
    }

    @Test fun `migration is idempotent`() {
        val cloudModel = Model(modelId = "cloud")
        val localModel = Model(modelId = "local.gguf")
        val settings = Settings(
            providers = listOf(
                ProviderSetting.LlamaCppLocal(models = listOf(localModel)),
                ProviderSetting.OpenAI(models = listOf(cloudModel)),
            ),
            chatModelId = localModel.id,
            assistants = listOf(Assistant(chatModelId = localModel.id)),
        )

        val once = settings.migrateAwayFromLocalModels()
        assertEquals(once, once.migrateAwayFromLocalModels())
    }

    @Test fun `migration returns the same settings when there is no local provider`() {
        val settings = Settings(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(Model(modelId = "cloud"))))
        )

        assertSame(settings, settings.migrateAwayFromLocalModels())
    }

    @Test fun `migration leaves settings untouched when no cloud fallback exists`() {
        val localModel = Model(modelId = "local.gguf")
        val settings = Settings(
            providers = listOf(ProviderSetting.LlamaCppLocal(models = listOf(localModel))),
            chatModelId = localModel.id,
            assistants = listOf(Assistant(chatModelId = localModel.id)),
        )

        assertSame(settings, settings.migrateAwayFromLocalModels())
    }
}
