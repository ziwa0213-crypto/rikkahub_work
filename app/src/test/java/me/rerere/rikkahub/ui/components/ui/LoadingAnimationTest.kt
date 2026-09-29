package me.rerere.rikkahub.ui.components.ui

import me.rerere.ai.provider.LoadingAnimationConfig
import me.rerere.ai.provider.LoadingAnimationMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingAnimationTest {
    @Test
    fun autoModeUsesDedicatedChannelAnimations() {
        assertEquals(
            ResolvedLoadingAnimation.Brand("openai.svg"),
            resolveLoadingAnimation(ProviderSetting.OpenAI(name = "OpenAI")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("claude-color.svg"),
            resolveLoadingAnimation(ProviderSetting.Claude(name = "Anthropic")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("gemini-color.svg"),
            resolveLoadingAnimation(ProviderSetting.Google(name = "Google")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("deepseek-color.svg"),
            resolveLoadingAnimation(ProviderSetting.OpenAI(name = "DeepSeek relay")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("qwen-color.svg"),
            resolveLoadingAnimation(ProviderSetting.OpenAI(name = "Qwen")),
        )
    }

    @Test
    fun autoModeFallsBackToProviderChannelForUnknownNames() {
        assertEquals(
            ResolvedLoadingAnimation.Brand("openai.svg"),
            resolveLoadingAnimation(ProviderSetting.OpenAI(name = "My relay")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("gemini-color.svg"),
            resolveLoadingAnimation(ProviderSetting.Google(name = "My relay")),
        )
        assertEquals(
            ResolvedLoadingAnimation.Brand("claude-color.svg"),
            resolveLoadingAnimation(ProviderSetting.Claude(name = "My relay")),
        )
        assertEquals(ResolvedLoadingAnimation.Material, resolveLoadingAnimation(null))
    }

    @Test
    fun invalidExplicitAnimationValuesRecoverToAutoMode() {
        val provider = ProviderSetting.OpenAI(
            name = "OpenAI relay",
            loadingAnimation = LoadingAnimationConfig(
                mode = LoadingAnimationMode.CUSTOM,
                customUri = "",
            ),
        )

        assertEquals(
            ResolvedLoadingAnimation.Brand("openai.svg"),
            resolveLoadingAnimation(provider),
        )
    }

    @Test
    fun providerLoadingAnimationConfigSurvivesSerialization() {
        val provider = ProviderSetting.OpenAI(
            loadingAnimation = LoadingAnimationConfig(
                mode = LoadingAnimationMode.CUSTOM,
                customUri = "file:///data/user/0/example.gif",
            ),
        )

        val restored = JsonInstant.decodeFromString<ProviderSetting.OpenAI>(
            JsonInstant.encodeToString(provider)
        )

        assertEquals(provider.loadingAnimation, restored.loadingAnimation)
    }

    @Test
    fun oldProviderDataUsesDefaultLoadingAnimationConfig() {
        val restored = JsonInstant.decodeFromString<ProviderSetting.OpenAI>("{}")

        assertEquals(LoadingAnimationConfig(), restored.loadingAnimation)
    }

    @Test
    fun legacyGlobalLoadingFieldRemainsDecodable() {
        val restored = JsonInstant.decodeFromString<DisplaySetting>(
            """{"useAppIconStyleLoadingIndicator":false}"""
        )

        assertEquals(false, restored.useAppIconStyleLoadingIndicator)
    }
}
