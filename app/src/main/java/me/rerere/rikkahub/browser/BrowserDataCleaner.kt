package me.rerere.rikkahub.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Clears the embedded WebView's login state and cache without touching app settings. */
object BrowserDataCleaner {
    suspend fun clear(context: Context) = withContext(Dispatchers.Main.immediate) {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies {
                cookies.flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

        WebStorage.getInstance().deleteAllData()
        runCatching { WebViewDatabase.getInstance(context.applicationContext).clearFormData() }
        WebView(context).apply {
            clearCache(true)
            clearFormData()
            destroy()
        }
    }

    fun cookieNames(host: String): List<String> {
        val normalized = normalizeBrowserVisitedHost(host) ?: return emptyList()
        val manager = CookieManager.getInstance()
        return listOf(
            "https://$normalized", "https://www.$normalized",
            "http://$normalized", "http://www.$normalized",
        )
            .flatMap { url -> cookieNamesFromHeader(manager.getCookie(url).orEmpty()) }
            .distinct()
    }

    fun hasCookies(host: String): Boolean = cookieNames(host).isNotEmpty()

    fun normalizeInputDomain(value: String): String? = normalizeBrowserVisitedHost(value)

    suspend fun clearCookiesForHost(host: String) = withContext(Dispatchers.Main.immediate) {
        val normalized = normalizeBrowserVisitedHost(host)
            ?: throw IllegalArgumentException("Invalid site host")
        val manager = CookieManager.getInstance()
        val urls = listOf(
            "https://$normalized", "https://www.$normalized",
            "http://$normalized", "http://www.$normalized",
        )
        val namesByUrl = urls.associateWith { url ->
            cookieNamesFromHeader(manager.getCookie(url).orEmpty())
        }
        namesByUrl.forEach { (url, names) ->
            names.forEach { name ->
                // Try both host-only and parent-domain cookies. CookieManager does not expose
                // Path/Domain, so cookies with a non-root Path may still survive this attempt.
                val secure = if (url.startsWith("https://")) "; Secure" else ""
                val hostOnlyExpiry = "$name=; Max-Age=0; Path=/$secure"
                val domainExpiry = "$name=; Max-Age=0; Path=/; Domain=$normalized$secure"
                val removalValues = listOf(
                    hostOnlyExpiry,
                    "$hostOnlyExpiry; HttpOnly",
                    domainExpiry,
                    "$domainExpiry; HttpOnly",
                )
                removalValues.forEach { value ->
                    suspendCancellableCoroutine { continuation ->
                        manager.setCookie(url, value) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
            }
        }
        manager.flush()
    }
}

internal fun cookieNamesFromHeader(header: String): List<String> = header
    .split(';')
    .mapNotNull { it.trim().substringBefore('=').takeIf(String::isNotBlank) }
    .distinct()
