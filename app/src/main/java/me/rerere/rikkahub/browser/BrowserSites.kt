package me.rerere.rikkahub.browser

import java.net.URI

/** Returns a canonical, privacy-preserving site host or null for local/IP/invalid URLs. */
fun normalizeBrowserVisitedHost(raw: String): String? {
    val value = raw.trim().lowercase()
    if (value.isEmpty() || value.any(Char::isWhitespace)) return null
    val withScheme = if (value.contains("://")) value else "https://$value"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawUserInfo != null) return null
    val host = uri.host
        ?.lowercase()
        ?.removePrefix("www.")
        ?.trimEnd('.')
        ?: return null
    if (host == "localhost" || host.endsWith(".localhost") || host.contains(':')) return null
    if (!host.contains('.') || host.length > 253) return null
    if (host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) return null
    if (host.split('.').any { label ->
            label.isEmpty() || label.length > 63 ||
                !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                label.any { !it.isLetterOrDigit() && it != '-' }
        }) return null
    return host
}

/** Throttle duplicate page-start writes without losing the most recently seen host. */
class BrowserVisitThrottle(private val intervalMs: Long = 5_000L) {
    private val lastWrittenAt = mutableMapOf<String, Long>()

    @Synchronized
    fun shouldRecord(host: String, nowElapsedMs: Long): Boolean {
        val previous = lastWrittenAt[host]
        if (previous != null && nowElapsedMs - previous < intervalMs) return false
        lastWrittenAt[host] = nowElapsedMs
        return true
    }
}
