package me.rerere.rikkahub.data.ai

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.provider.ClientContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.isDeepSeekDialect
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock

internal fun buildClientContext(
    now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    locale: Locale = Locale.getDefault(),
): ClientContext = ClientContext(
    date = now.date.toString(),
    weekday = java.time.LocalDate.parse(now.date.toString()).dayOfWeek.getDisplayName(TextStyle.FULL, locale),
    language = deepSeekLanguageTag(locale),
)

internal fun deepSeekLanguageTag(locale: Locale): String = when (locale.language) {
    "zh" -> when (locale.country.uppercase(Locale.ROOT)) {
        "TW" -> "中文繁體"
        "HK", "MO" -> "中文香港"
        else -> "中文"
    }
    else -> locale.getDisplayLanguage(locale).ifBlank { "English" }
}

internal fun usesLatestReminder(provider: ProviderSetting, modelId: String): Boolean =
    provider is ProviderSetting.OpenAI && !provider.useResponseApi &&
        isDeepSeekDialect(runCatching { provider.baseUrl.toHttpUrl().host }.getOrDefault(""), modelId)
