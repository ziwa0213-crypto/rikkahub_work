package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.Modality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `deepseek web defaults preserve provider identity and support images`() {
        val provider = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.DeepSeekWeb>().single()
        assertEquals("8b2cf7f2-66f5-4f9f-9c26-4d0e2b4f7b31", provider.id.toString())
        assertEquals(listOf("deepseek-web", "deepseek-web-thinking"), provider.models.map { it.modelId })
        provider.models.forEach { assertTrue(Modality.IMAGE in it.inputModalities) }
    }

    @Test
    fun `default providers should include vercel ai gateway with expected balance config`() {
        val vercelProviders = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "Vercel AI Gateway" }

        assertEquals(1, vercelProviders.size)

        val provider = vercelProviders.single()
        assertEquals("https://ai-gateway.vercel.sh/v1", provider.baseUrl)
        assertFalse(provider.enabled)
        assertTrue(provider.builtIn)
        assertTrue(provider.balanceOption.enabled)
        assertEquals("/credits", provider.balanceOption.apiPath)
        assertEquals("balance", provider.balanceOption.resultPath)
    }
}
