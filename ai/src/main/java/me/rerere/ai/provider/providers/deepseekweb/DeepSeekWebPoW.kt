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
import kotlinx.serialization.json.jsonPrimitive
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
    private const val WASM_ASSET = "deepseek_sha3.wasm"

    suspend fun createHeader(
        client: OkHttpClient,
        context: Context,
        headers: Map<String, String>,
        targetPath: String = TARGET,
    ): String {
        val request = Request.Builder()
            .url("$BASE_URL/api/v0/chat/create_pow_challenge")
            .headers(headers.toHeaders())
            .post(buildJsonObject { put("target_path", targetPath) }.toString().toRequestBody(JSON))
            .build()
        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        check(response.isSuccessful) { "DeepSeek PoW challenge failed: ${response.code}" }
        val challenge = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("biz_data")?.jsonObject?.get("challenge")?.jsonObject
            ?: error("DeepSeek PoW challenge missing")
        val answer = solve(context, challenge)
        return headerPayload(challenge, answer, targetPath)
    }

    private suspend fun solve(context: Context, challenge: JsonObject): Int {
        val challengeText = challenge["challenge"]?.toString()?.trim('"') ?: error("challenge missing")
        val salt = challenge["salt"]?.toString()?.trim('"') ?: error("salt missing")
        val expireAt = challenge["expire_at"]?.toString()?.trim('"') ?: error("expire_at missing")
        val difficulty = challenge["difficulty"]?.toString()?.trim('"') ?: error("difficulty missing")
        val wasm = withContext(Dispatchers.IO) {
            context.assets.open(WASM_ASSET).use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        }
        val script = """
            (function() {
              try {
              const bytes = Uint8Array.from(atob('$wasm'), c => c.charCodeAt(0));
              const module = new WebAssembly.Module(bytes);
              const instance = new WebAssembly.Instance(module, {wbg: {}});
              const e = instance.exports;
              if (typeof e.wasm_solve !== 'function' || typeof e.__wbindgen_export_0 !== 'function' || !e.memory) {
                throw new Error('PoW WASM exports missing');
              }
              const enc = new TextEncoder();
              const c = enc.encode(${jsString(challengeText)});
              const p = enc.encode(${jsString("${salt}_${expireAt}_")});
              const cp = e.__wbindgen_export_0(c.length, 1) >>> 0;
              const pp = e.__wbindgen_export_0(p.length, 1) >>> 0;
              new Uint8Array(e.memory.buffer).set(c, cp);
              new Uint8Array(e.memory.buffer).set(p, pp);
              const sp = e.__wbindgen_add_to_stack_pointer(-16);
              let code;
              let answer;
              try {
                e.wasm_solve(sp, cp, c.length, pp, p.length, Number(${jsString(difficulty)}));
                const view = new DataView(e.memory.buffer);
                code = view.getInt32(sp, true);
                answer = view.getFloat64(sp + 8, true);
              } finally {
                e.__wbindgen_add_to_stack_pointer(16);
              }
              if (code === 0 || !Number.isFinite(answer) || answer <= 0) throw new Error('PoW solve failed');
              return 'OK:' + String(Math.floor(answer));
              } catch (error) {
                return 'ERR:' + String(error && error.message ? error.message : error);
              }
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
                                val answer = parseJavascriptResult(result)
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
    internal fun parseJavascriptResult(result: String): Int {
        val value = runCatching { json.parseToJsonElement(result).jsonPrimitive.content }
            .getOrElse { error("DeepSeek PoW WebView returned invalid result: $result") }
        return when {
            value.startsWith("OK:") -> value.removePrefix("OK:").toIntOrNull()
                ?: error("DeepSeek PoW returned an invalid answer")

            value.startsWith("ERR:") -> error(value.removePrefix("ERR:").ifBlank { "unknown error" })
            else -> error("unexpected WebView result: $value")
        }
    }

    @VisibleForTesting
    internal fun headerPayload(challenge: JsonObject, answer: Int, targetPath: String = TARGET): String {
        val payload = buildJsonObject {
            challenge["algorithm"]?.let { put("algorithm", it) }
            put("challenge", challenge["challenge"] ?: error("challenge missing"))
            put("salt", challenge["salt"] ?: error("salt missing"))
            put("answer", answer)
            put("signature", challenge["signature"] ?: error("signature missing"))
            put("target_path", targetPath)
        }
        return java.util.Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers = okhttp3.Headers.Builder().apply {
        forEach { (name, value) -> add(name, value) }
    }.build()

    private fun jsString(value: String): String = kotlinx.serialization.json.JsonPrimitive(value).toString()

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
