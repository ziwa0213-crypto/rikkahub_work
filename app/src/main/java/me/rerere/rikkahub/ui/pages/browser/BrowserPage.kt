package me.rerere.rikkahub.ui.pages.browser

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup.LayoutParams
import android.webkit.WebResourceRequest
import android.webkit.WebView as AndroidWebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.BrowserVisitThrottle
import me.rerere.rikkahub.browser.normalizeBrowserVisitedHost
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebViewState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun BrowserPage(
    conversationId: Uuid, initialUrl: String,
    standalone: Boolean = false,
    controller: BrowserController = koinInject(), settingsStore: SettingsStore = koinInject(),
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val state = remember(conversationId) { WebViewState() }
    val visitThrottle = remember { BrowserVisitThrottle() }
    val toaster = LocalToaster.current
    val failure = stringResource(R.string.error_title_operation)
    var editAddress by remember { mutableStateOf(standalone && initialUrl.isBlank()) }
    var address by remember { mutableStateOf(initialUrl) }
    var addressError by remember { mutableStateOf(false) }
    DisposableEffect(state) {
        onDispose {
            controller.flushCookies()
            controller.detach(state)
        }
    }
    BackHandler(enabled = state.canGoBack && !editAddress) { state.goBack() }
    fun navigate() {
        if (BrowserController.validateUrl(address) != null) { addressError = true; return }
        scope.launch {
            controller.open(address, conversationId).onSuccess { editAddress = false }
                .onFailure { toaster.show(failure, type = ToastType.Error) }
        }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column(Modifier.clickable {
                            address = state.currentUrl ?: initialUrl; addressError = false; editAddress = true
                        }) {
                            Text(state.pageTitle?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.assistant_page_local_tools_browser_title),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(state.currentUrl ?: initialUrl, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { BackButton() },
                    colors = CustomColors.topBarColors,
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = state::goBack, enabled = state.canGoBack) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.browser_ui_back))
                    }
                    IconButton(onClick = state::goForward, enabled = state.canGoForward) {
                        Icon(HugeIcons.ArrowRight01, stringResource(R.string.browser_ui_forward))
                    }
                    IconButton(onClick = { state.webView?.reload() }) {
                        Icon(HugeIcons.Refresh01, stringResource(R.string.browser_ui_reload))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        listOf(false, true).forEachIndexed { index, desktop ->
                            SegmentedButton(selected = settings.browserDesktopMode == desktop,
                                shape = SegmentedButtonDefaults.itemShape(index, 2), onClick = {
                                    if (settings.browserDesktopMode != desktop) scope.launch {
                                        settingsStore.setBrowserDesktopMode(desktop)
                                        controller.setDesktopMode(desktop)
                                    }
                                }) { Text(stringResource(if (desktop) R.string.browser_mode_desktop else R.string.browser_mode_mobile), maxLines = 1) }
                        }
                    }
                }
                if (state.isLoading) LinearProgressIndicator(progress = { state.loadingProgress }, modifier = Modifier.fillMaxWidth())
            }
        }, containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Browser-specific host: never reload the original URL on recomposition.
            // Shared WebViewPage/cache behavior is deliberately left untouched.
            BrowserWebViewHost(
                state, conversationId, initialUrl, settings.browserDesktopMode, standalone,
                controller, settingsStore, visitThrottle, scope,
            )
            if (!standalone) {
                BrowserMiniPanel(conversationId, controller, settingsStore, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
    if (editAddress) AlertDialog(
        onDismissRequest = { editAddress = false },
        title = { Text(stringResource(R.string.browser_ui_edit_address)) },
        text = { OutlinedTextField(address, { address = it; addressError = false }, isError = addressError,
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { navigate() })) },
        confirmButton = { TextButton(onClick = { navigate() }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { editAddress = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebViewHost(
    state: WebViewState,
    conversationId: Uuid,
    initialUrl: String,
    desktop: Boolean,
    standalone: Boolean,
    controller: BrowserController,
    settingsStore: SettingsStore,
    visitThrottle: BrowserVisitThrottle,
    scope: CoroutineScope,
) {
    val busy = stringResource(R.string.browser_ui_busy)
    val toaster = LocalToaster.current
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        AndroidWebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                builtInZoomControls = true; displayZoomControls = false
                allowContentAccess = false; allowFileAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            state.webView = this
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: AndroidWebView, request: WebResourceRequest): Boolean =
                    BrowserController.validateUrl(request.url.toString()) != null
                override fun onPageStarted(view: AndroidWebView, url: String?, favicon: Bitmap?) {
                    state.currentUrl = url; state.isLoading = true
                    val host = normalizeBrowserVisitedHost(url.orEmpty()) ?: return
                    if (visitThrottle.shouldRecord(host, SystemClock.elapsedRealtime())) {
                        scope.launch { settingsStore.recordBrowserVisitedHost(host) }
                    }
                }
                override fun onPageFinished(view: AndroidWebView, url: String?) {
                    state.currentUrl = url; state.pageTitle = view.title; state.isLoading = false
                    state.canGoBack = view.canGoBack(); state.canGoForward = view.canGoForward()
                    controller.flushCookies()
                    if (controller.active?.state === state && controller.active?.desktopMode == true) {
                        applyDesktopViewport(view)
                        view.postDelayed({
                            if (controller.active?.state === state && controller.active?.desktopMode == true) {
                                applyDesktopViewport(view)
                            }
                        }, 300L)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: AndroidWebView, newProgress: Int) { state.loadingProgress = newProgress / 100f }
                override fun onReceivedTitle(view: AndroidWebView, title: String?) { state.pageTitle = title }
                // Do not copy web console content (which may contain credentials) to logs.
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?) = true
            }
            if (controller.active == null) {
                controller.attach(conversationId, state, desktop, preserveAiTask = !standalone)
                if (BrowserController.validateUrl(initialUrl) == null) loadUrl(initialUrl)
            } else toaster.show(busy, type = ToastType.Error)
        }
    }, onRelease = { view ->
        controller.detach(state)
        if (state.webView === view) state.webView = null
        view.stopLoading(); view.webChromeClient = null; view.webViewClient = WebViewClient(); view.destroy()
        // No storage/cache/history clearing. A mode change never releases this host.
    })
}

private fun applyDesktopViewport(view: AndroidWebView) {
    runCatching { view.evaluateJavascript(me.rerere.rikkahub.browser.BrowserDesktopViewport.SCRIPT, null) }
}
