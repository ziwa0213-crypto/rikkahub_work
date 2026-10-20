package me.rerere.rikkahub.browser

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/** Optional main-content extraction. Failure always falls back to body text. */
internal object ReadabilityRunner {
    private const val ASSET = "browser/readability.js"

    @Volatile
    private var library: String? = null

    suspend fun extract(webView: WebView): String? {
        val source = library ?: runCatching {
            webView.context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.also { library = it } ?: return null
        val script = """
            (function(){
              try {
                if (typeof Readability === 'undefined') { $source }
                ${BrowserJsScripts.cleanDocument}
                var article = new Readability(cleanDocument()).parse();
                return JSON.stringify(article ? {text: (article.textContent || '').trim().slice(0, 120000)} : null);
              } catch (_) { return JSON.stringify(null); }
            })()
        """.trimIndent()
        val raw = withTimeoutOrNull(4_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<String> { continuation ->
                    webView.evaluateJavascript(script) { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                }
            }
        } ?: return null
        return runCatching {
            val outer = Json.parseToJsonElement(raw)
            val inner = if (outer is JsonPrimitive && outer.isString) {
                Json.parseToJsonElement(outer.content)
            } else outer
            inner.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
