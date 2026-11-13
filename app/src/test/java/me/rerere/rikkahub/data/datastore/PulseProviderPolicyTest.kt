package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class PulseProviderPolicyTest {
    @Test fun `only pulse model is cleared while global web settings and provider remain`() {
        val model = Model(modelId = "web")
        val web = ProviderSetting.DeepSeekWeb(models = listOf(model))
        val settings = Settings(providers = listOf(web), chatModelId = model.id, fastModelId = model.id,
            pulse = PulseConfig(chatModelId = model.id))
        val normalized = settings.normalized()
        assertNull(normalized.pulse.chatModelId)
        assertEquals(model.id, normalized.chatModelId)
        assertEquals(model.id, normalized.fastModelId)
        assertTrue(normalized.providers.any { it is ProviderSetting.DeepSeekWeb && it.id == web.id })
        assertTrue(normalized.pulseUsesWebProvider())
    }

    @Test fun `API models remain valid regardless of deepseek naming and override cannot bypass web restriction`() {
        val api = Model(modelId = "deepseek", displayName = "DeepSeek web sounding name")
        val provider = ProviderSetting.OpenAI(models = listOf(api))
        assertTrue(api.isAllowedForPulse(listOf(provider)))
        val web = ProviderSetting.DeepSeekWeb(models = listOf(api.copy(providerOverwrite = provider)))
        assertFalse(web.models.single().isAllowedForPulse(listOf(web)))
        val overwritten = api.copy(providerOverwrite = ProviderSetting.DeepSeekWeb())
        assertFalse(overwritten.isAllowedForPulse(listOf(provider.copy(models = listOf(overwritten)))))
    }
}
