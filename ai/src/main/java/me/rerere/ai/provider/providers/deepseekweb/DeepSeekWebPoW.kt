package me.rerere.ai.provider.providers.deepseekweb

import android.content.Context
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.util.json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import me.rerere.common.http.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object DeepSeekWebPoW {
    private const val BASE_URL = "https://chat.deepseek.com"
    private const val TARGET = "/api/v0/chat/completion"

    suspend fun createHeader(
        client: OkHttpClient,
        context: Context,
        headers: Map<String, String>,
    ): String {
        val request = Request.Builder()
            .url("$BASE_URL/api/v0/chat/create_pow_challenge")
            .headers(headers.toHeaders())
            .post("{\"target_path\":\"$TARGET\"}".toRequestBody(JSON))
            .build()
        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        check(response.isSuccessful) { "DeepSeek PoW challenge failed: ${response.code}" }
        val challenge = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("biz_data")?.jsonObject?.get("challenge")?.jsonObject
            ?: error("DeepSeek PoW challenge missing")
        val answer = solve(context, challenge)
        val payload = buildJsonObject {
            challenge["algorithm"]?.let { put("algorithm", it) }
            put("challenge", challenge["challenge"] ?: error("challenge missing"))
            put("salt", challenge["salt"] ?: error("salt missing"))
            put("answer", answer)
            put("signature", challenge["signature"] ?: error("signature missing"))
            put("target_path", TARGET)
        }
        return Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private suspend fun solve(context: Context, challenge: JsonObject): Int {
        val challengeText = challenge["challenge"]?.toString()?.trim('"') ?: error("challenge missing")
        val salt = challenge["salt"]?.toString()?.trim('"') ?: error("salt missing")
        val expireAt = challenge["expire_at"]?.toString()?.trim('"') ?: error("expire_at missing")
        val difficulty = challenge["difficulty"]?.toString()?.trim('"') ?: error("difficulty missing")
        val wasm = withContext(Dispatchers.IO) {
            context.assets.open("deepseek_sha3.wasm").use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        }
        val script = """
            (async function() {
              const bytes = Uint8Array.from(atob('$wasm'), c => c.charCodeAt(0));
              const instance = await WebAssembly.instantiate(bytes, {wbg: {}});
              const e = instance.instance.exports;
              const enc = new TextEncoder();
              const c = enc.encode('$challengeText');
              const p = enc.encode('${salt}_${expireAt}_');
              const cp = e.__wbindgen_export_0(c.length, 1) >>> 0;
              const pp = e.__wbindgen_export_0(p.length, 1) >>> 0;
              new Uint8Array(e.memory.buffer).set(c, cp);
              new Uint8Array(e.memory.buffer).set(p, pp);
              const sp = e.__wbindgen_add_to_stack_pointer(-16);
              e.wasm_solve(sp, cp, c.length, pp, p.length, Number('$difficulty'));
              const view = new DataView(e.memory.buffer);
              const code = view.getInt32(sp, true);
              const answer = view.getFloat64(sp + 8, true);
              e.__wbindgen_add_to_stack_pointer(16);
              if (code === 0 || !Number.isFinite(answer) || answer <= 0) throw new Error('PoW solve failed');
              return String(Math.floor(answer));
            })()
        """.trimIndent()
        val value = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context.applicationContext)
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(script) { result ->
                            try {
                                val answer = result.trim().removeSurrounding("\"").toInt()
                                if (continuation.isActive) continuation.resume(answer)
                            } catch (error: Throwable) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            } finally {
                                view.destroy()
                            }
                        }
                    }
                }
                continuation.invokeOnCancellation { webView.post { webView.destroy() } }
                webView.loadDataWithBaseURL("https://chat.deepseek.com/", "<html></html>", "text/html", "UTF-8", null)
            }
        }
        return value
    }

    @VisibleForTesting
    internal fun headerPayload(challenge: JsonObject, answer: Int): String {
        val payload = buildJsonObject {
            challenge["algorithm"]?.let { put("algorithm", it) }
            put("challenge", challenge["challenge"] ?: error("challenge missing"))
            put("salt", challenge["salt"] ?: error("salt missing"))
            put("answer", answer)
            put("signature", challenge["signature"] ?: error("signature missing"))
            put("target_path", TARGET)
        }
        return Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers = okhttp3.Headers.Builder().apply {
        forEach { (name, value) -> add(name, value) }
    }.build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
