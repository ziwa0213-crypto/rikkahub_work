package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    var diag by remember { mutableStateOf("") }
    var loadState by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.deepseek_web_login_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (captured) R.string.deepseek_web_login_captured else R.string.deepseek_web_login_hint))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(webViewHeight),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            WebView(context).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(false)
                                settings.builtInZoomControls = false
                                settings.displayZoomControls = false
                                settings.textZoom = 100
                                val rawUa = settings.userAgentString
                                val effectiveUa = rawUa.replace("; wv", "").replace("Version/4.0 ", "")
                                settings.userAgentString = effectiveUa
                                Log.i(TAG, "UA raw=$rawUa")
                                Log.i(TAG, "UA effective=$effectiveUa")
                                Log.i(TAG, "WebView pkg=" + WebView.getCurrentWebViewPackage())
                                isHorizontalScrollBarEnabled = false
                                setBackgroundColor(Color.WHITE)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                                        capture(view, request.url.toString(), request.requestHeaders)

                                    override fun onPageFinished(view: WebView, url: String) {
                                        if (loadState !is LoadState.Failed) loadState = LoadState.Done
                                        view.evaluateJavascript(DEEPSEEK_INPUT_FIX_SCRIPT, null)
                                        view.evaluateJavascript(
                                            "JSON.stringify({href:location.href,title:document.title," +
                                                "len:(document.body?document.body.innerText.length:-1)})"
                                        ) { json ->
                                            Log.i(TAG, "probe=$json")
                                            diag = json.orEmpty()
                                        }
                                        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                                        if (cookie.isNotBlank()) {
                                            val token = capturedToken
                                            if (!token.isNullOrBlank()) {
                                                captured = true
                                                onCaptured(token, cookie, capturedHeaders)
                                            }
                                        }
                                    }

                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        loadState = LoadState.Loading
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        Log.w(TAG, "error url=${request.url} code=${error.errorCode} description=${error.description}")
                                        if (request.isForMainFrame) {
                                            loadState = LoadState.Failed(
                                                code = error.errorCode,
                                                message = error.description?.toString().orEmpty(),
                                            )
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        response: WebResourceResponse,
                                    ) {
                                        Log.w(TAG, "error url=${request.url} code=${response.statusCode} description=${response.reasonPhrase}")
                                        if (request.isForMainFrame) {
                                            loadState = LoadState.Failed(
                                                code = response.statusCode,
                                                message = response.reasonPhrase.orEmpty(),
                                            )
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
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                        Log.w(
                                            TAG,
                                            "[console] ${msg.messageLevel()} ${msg.message()} " +
                                                "@${msg.sourceId()}:${msg.lineNumber()}",
                                        )
                                        return true
                                    }
                                }
                                loadUrl(DEEPSEEK_LOGIN_URL)
                            }
                        },
                        onRelease = { webView ->
                            if (webViewRef === webView) webViewRef = null
                            webView.stopLoading()
                            webView.destroy()
                        },
                    )

                    when (val state = loadState) {
                        LoadState.Loading -> LoginLoadingOverlay()
                        is LoadState.Failed -> LoginFailedOverlay(
                            state = state,
                            onRetry = {
                                loadState = LoadState.Loading
                                webViewRef?.loadUrl(DEEPSEEK_LOGIN_URL)
                            },
                        )
                        LoadState.Done -> if (!captured) {
                            LoginBlankHint(
                                diag = diag,
                                onOpenBrowser = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(DEEPSEEK_LOGIN_URL))
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun LoginBlankHint(
    diag: String,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.deepseek_web_login_manual_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            if (diag.isNotBlank()) {
                Text(
                    text = stringResource(R.string.deepseek_web_login_diag, diag.take(240)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenBrowser) {
                Text(stringResource(R.string.deepseek_web_login_open_browser))
            }
        }
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data object Done : LoadState
    data class Failed(val code: Int, val message: String) : LoadState
}

@Composable
private fun LoginLoadingOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.deepseek_web_login_loading),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun LoginFailedOverlay(
    state: LoadState.Failed,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    R.string.deepseek_web_login_load_failed,
                    state.code,
                    state.message.ifBlank { stringResource(R.string.deepseek_web_login_unknown_error) },
                ),
                color = MaterialTheme.colorScheme.error,
            )
            if (state.code in setOf(WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT)) {
                Text(
                    text = stringResource(R.string.deepseek_web_login_proxy_hint),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.deepseek_web_login_retry))
            }
        }
    }
}

private const val DEEPSEEK_LOGIN_URL = "https://chat.deepseek.com"
private const val TAG = "DeepSeekWebLogin"

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
