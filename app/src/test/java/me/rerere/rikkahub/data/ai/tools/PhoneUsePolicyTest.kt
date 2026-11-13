package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class PhoneUsePolicyTest {
    @Test fun `phone use requires chat mode and blocks both web ownership and web override`() {
        val model = Model()
        val api = ProviderSetting.OpenAI(models = listOf(model))
        assertTrue(shouldOfferPhoneUse(true, model, listOf(api)))
        assertFalse(shouldOfferPhoneUse(false, model, listOf(api)))
        assertFalse(shouldOfferPhoneUse(true, model, listOf(ProviderSetting.DeepSeekWeb(models = listOf(model)))))
        val override = model.copy(providerOverwrite = ProviderSetting.DeepSeekWeb())
        assertFalse(shouldOfferPhoneUse(true, override, listOf(api.copy(models = listOf(override)))))
    }
}
