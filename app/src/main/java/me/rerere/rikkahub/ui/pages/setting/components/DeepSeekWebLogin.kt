package me.rerere.rikkahub.ui.pages.setting.components

import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.R

@Composable
internal fun DeepSeekWebLogin(
    onCaptured: (token: String, cookie: String, headers: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val webViewHeight = (configuration.screenHeightDp - 260).coerceIn(320, 560).dp
    var captured by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.deepseek_web_login_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (captured) R.string.deepseek_web_login_captured else R.string.deepseek_web_login_hint))
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(webViewHeight),
                    factory = {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.textZoom = 100
                            isHorizontalScrollBarEnabled = false
                            setBackgroundColor(Color.WHITE)
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                                    capture(view, request.url.toString(), request.requestHeaders)

                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(DEEPSEEK_INPUT_FIX_SCRIPT, null)
                                    val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                                    if (cookie.isNotBlank()) {
                                        val token = capturedToken
                                        if (!token.isNullOrBlank()) {
                                            captured = true
                                            onCaptured(token, cookie, capturedHeaders)
                                        }
                                    }
                                }

                                private var capturedToken: String? = null
                                private var capturedHeaders: Map<String, String> = emptyMap()

                                private fun capture(view: WebView, url: String, headers: Map<String, String>): android.webkit.WebResourceResponse? {
                                    if (!url.startsWith("https://chat.deepseek.com")) return null
                                    val authorization = headers.entries.firstOrNull { it.key.equals("authorization", true) }?.value
                                    if (!authorization.isNullOrBlank()) {
                                        capturedToken = authorization.removePrefix("Bearer ").trim()
                                        capturedHeaders = headers.filterKeys { key ->
                                            key.startsWith("x-", true) || key.equals("user-agent", true)
                                        }
                                        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                                        if (cookie.isNotBlank()) {
                                            captured = true
                                            onCaptured(capturedToken.orEmpty(), cookie, capturedHeaders)
                                        }
                                    }
                                    return null
                                }
                            }
                            loadUrl("https://chat.deepseek.com")
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private val DEEPSEEK_INPUT_FIX_SCRIPT = """
    (function() {
      if (window.__rikkahubDeepSeekInputFix) return;
      window.__rikkahubDeepSeekInputFix = true;

      function moveCaretToEnd(input) {
        if (document.activeElement !== input) return;
        var end = (input.value || '').length;
        try { input.setSelectionRange(end, end); } catch (_) {}
      }

      function patchInput(input) {
        if (!(input instanceof HTMLInputElement) || input.dataset.rikkaCaretFix === '1') return;
        if (input.type === 'number') {
          input.type = 'text';
          input.inputMode = 'numeric';
        }
        input.dataset.rikkaCaretFix = '1';
        input.addEventListener('input', function() {
          var current = this;
          moveCaretToEnd(current);
          requestAnimationFrame(function() { moveCaretToEnd(current); });
          setTimeout(function() { moveCaretToEnd(current); }, 0);
          setTimeout(function() { moveCaretToEnd(current); }, 50);
        }, true);
      }

      function scanInputs() {
        document.querySelectorAll('input').forEach(patchInput);
      }

      scanInputs();
      new MutationObserver(scanInputs).observe(document.documentElement, {
        childList: true,
        subtree: true
      });
    })();
""".trimIndent()
