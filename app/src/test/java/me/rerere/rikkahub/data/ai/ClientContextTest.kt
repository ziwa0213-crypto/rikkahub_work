package me.rerere.rikkahub.data.ai

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.toAssistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ClientContextTest {
    @Test fun `locale names and weekdays follow requested language`() {
        val now = LocalDateTime(2026, 11, 20, 12, 0)
        assertEquals("2026-11-20,星期五,,App,中文", buildClientContext(now, Locale.SIMPLIFIED_CHINESE).toLatestReminderContent())
        assertEquals("2026-11-20,Friday,,App,English", buildClientContext(now, Locale.ENGLISH).toLatestReminderContent())
        assertEquals("中文繁體", buildClientContext(now, Locale.TAIWAN).language)
        assertEquals("中文香港", deepSeekLanguageTag(Locale.forLanguageTag("zh-HK")))
        assertEquals("中文香港", deepSeekLanguageTag(Locale.forLanguageTag("zh-MO")))
        assertEquals("日本語", deepSeekLanguageTag(Locale.JAPANESE))
    }

    @Test fun `only deepseek chat completions routes to reminder role`() {
        assertTrue(usesLatestReminder(ProviderSetting.OpenAI(baseUrl = "https://api.deepseek.com/v1"), "alias"))
        assertTrue(usesLatestReminder(ProviderSetting.OpenAI(baseUrl = "https://relay.example"), "DeepSeek-chat"))
        assertFalse(usesLatestReminder(ProviderSetting.OpenAI(useResponseApi = true), "deepseek-chat"))
        assertFalse(usesLatestReminder(ProviderSetting.Claude(), "deepseek-chat"))
        assertFalse(usesLatestReminder(ProviderSetting.Google(), "gemini"))
        assertFalse(usesLatestReminder(ProviderSetting.OpenAI(), "gpt-4o"))
        assertFalse(usesLatestReminder(ProviderSetting.DeepSeekWeb(), "deepseek"))
    }

    @Test fun `new assistants enable context while old JSON keeps old tools`() {
        assertTrue(LocalToolOption.ClientContext in Assistant().localTools)
        val legacy = JsonInstant.decodeFromString<Assistant>("""{"name":"legacy"}""")
        assertEquals(listOf(LocalToolOption.TimeInfo), legacy.localTools)
        assertEquals(emptyList<LocalToolOption>(), JsonInstant.decodeFromString<Assistant>("""{"localTools":[]}""").localTools)
        assertEquals(Assistant().localTools, Json.decodeFromString<Assistant>(Json.encodeToString(Assistant())).localTools)
        assertEquals(legacy, JsonInstant.decodeFromString<Assistant>(JsonInstant.encodeToString(legacy)))
    }

    @Test fun `old pulse remains off and explicit preferences round trip`() {
        assertTrue(PulseConfig().enableClientContext)
        assertFalse(JsonInstant.decodeFromString<PulseConfig>("{}").enableClientContext)
        for (enabled in listOf(false, true)) {
            val config = PulseConfig(enableClientContext = enabled, enableInformation = false)
            assertEquals(config, Json.decodeFromString<PulseConfig>(Json.encodeToString(config)))
            assertEquals(enabled, LocalToolOption.ClientContext in config.toAssistant(Settings()).localTools)
        }
    }
}
