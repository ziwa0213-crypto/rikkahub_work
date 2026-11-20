package me.rerere.ai.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingForkCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun `old video ability stays readable and model template is optional and round trips`() {
        val old = json.decodeFromString<Model>("""{"modelId":"old","abilities":["VIDEO"]}""")
        assertEquals(listOf(ModelAbility.VIDEO), old.abilities)
        assertEquals(null, old.chatTemplate)
        val edited = old.copy(chatTemplate = "{% for m in messages %}{{ m.content }}{% endfor %} 🌍")
        assertEquals(edited, json.decodeFromString<Model>(json.encodeToString(edited)))
    }

    @Test
    fun `all provider types preserve fork settings when copied and serialized`() {
        val animation = LoadingAnimationConfig(LoadingAnimationMode.PRESET, presetId = "deepseek")
        val balance = BalanceOption(true, "/balance", "remaining")
        val headers = listOf(CustomHeader("X-Test", "test"))
        val providers = listOf(
            ProviderSetting.OpenAI(), ProviderSetting.Google(), ProviderSetting.Claude(),
            ProviderSetting.ChatGPT(), ProviderSetting.Grok(), ProviderSetting.DeepSeekWeb(), ProviderSetting.LlamaCppLocal(),
        )
        providers.forEach { provider ->
            val copy = provider.copyProvider(
                name = "copied", loadingAnimation = animation, balanceOption = balance, customHeaders = headers,
            )
            assertEquals(animation, copy.loadingAnimation)
            assertEquals(balance, copy.balanceOption)
            assertEquals(headers, copy.customHeaders)
            val decoded = json.decodeFromString<ProviderSetting>(json.encodeToString(copy))
            assertEquals(copy, decoded)
        }
    }

    @Test fun `local provider defaults use stable identity and never participate in API conversion`() {
        val first = json.decodeFromString<ProviderSetting>("""{"type":"local_llamacpp"}""") as ProviderSetting.LlamaCppLocal
        val second = ProviderSetting.LlamaCppLocal()
        assertEquals(LLAMACPP_PROVIDER_ID, first.id)
        assertEquals(first.id, second.id)
        assertTrue(first.enabled)
        assertTrue(first.builtIn)
        assertEquals("https://huggingface.co", first.downloadBase)
        assertTrue(ProviderSetting.Types.none { it.qualifiedName == ProviderSetting.LlamaCppLocal::class.qualifiedName })
        assertFalse(first.supportsQuota)
        assertFalse(first.supportsBuiltInSearch)
    }

    @Test
    fun `old deepseek web settings still decode without account fields`() {
        val setting = json.decodeFromString<ProviderSetting>(
            """{"type":"deepseek_web","token":"test-token","cookie":"test-cookie","accountHint":"test-account"}"""
        ) as ProviderSetting.DeepSeekWeb
        assertEquals("test-token", setting.token)
        assertEquals("test-cookie", setting.cookie)
        assertEquals(LoadingAnimationConfig(), setting.loadingAnimation)
        assertFalse(setting.supportsBuiltInSearch)
        assertFalse(setting.supportsQuota)
    }

    @Test
    fun `legacy reasoning preference remains configurable`() {
        val default = json.decodeFromString<ProviderSetting>("""{"type":"openai"}""") as ProviderSetting.OpenAI
        assertTrue(default.includeHistoryReasoning)
        val disabled = json.decodeFromString<ProviderSetting>(
            """{"type":"openai","includeHistoryReasoning":false}"""
        ) as ProviderSetting.OpenAI
        assertFalse(disabled.includeHistoryReasoning)
        assertFalse((disabled.copyProvider(name = "copy") as ProviderSetting.OpenAI).includeHistoryReasoning)
    }
}
