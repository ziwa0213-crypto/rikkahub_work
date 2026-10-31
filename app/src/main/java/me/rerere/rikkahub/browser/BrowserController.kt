package me.rerere.rikkahub.browser

import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.ui.webview.WebViewState
import java.net.URI
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

class BrowserException(val code: String, message: String) : IllegalStateException(message)
data class BrowserClickable(val id: String, val tag: String, val type: String, val text: String)
data class BrowserPageState(val title: String, val url: String, val loading: Boolean, val desktopMode: Boolean)

/** The one visible browser is owned by one originating conversation. */
class BrowserController(private val settingsStore: SettingsStore) {
    data class ActiveBrowser(
        val conversationId: Uuid, val state: WebViewState, val desktopMode: Boolean,
        val mobileUserAgent: String, val defaultUserAgent: String,
    )
    @Volatile var active: ActiveBrowser? = null
        private set
    private val _filteredCount = MutableStateFlow(0)
    val filteredCount = _filteredCount.asStateFlow()
    fun recordFilteredCount(count: Int) { _filteredCount.value = count }

    @MainThread
    fun attach(conversationId: Uuid, state: WebViewState, desktopMode: Boolean, preserveAiTask: Boolean = false) {
        check(active == null || active?.state === state) { "A browser is already open" }
        val view = state.webView ?: return
        val defaultUa = WebSettings.getDefaultUserAgent(view.context)
        val mobileUa = settingsStore.settingsFlow.value.networkSetting.userAgent.trim().ifBlank { defaultUa }
        active = ActiveBrowser(conversationId, state, desktopMode, mobileUa, defaultUa)
        taskWindow.onAttach(conversationId, preserveAiTask)
        configureMode(view, desktopMode, mobileUa, defaultUa)
        _filteredCount.value = 0
    }

    @MainThread fun detach(state: WebViewState) {
        if (active?.state === state) {
            active = null
            taskWindow.reset()
        }
    }

    @MainThread
    fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }

    fun accessError(conversationId: Uuid?): BrowserException? = when {
        active == null -> BrowserException("NO_BROWSER", "No browser page is open. Call browser_open first (this asks the user for approval).")
        conversationId == null || active?.conversationId != conversationId -> BrowserException("BROWSER_BUSY", "This browser belongs to another conversation.")
        else -> null
    }

    private val taskWindow = BrowserTaskWindow()

    private suspend fun <T> action(
        conversationId: Uuid?,
        aiCall: Boolean = false,
        block: suspend (ActiveBrowser) -> T,
    ): Result<T> = try {
        withContext(Dispatchers.Main.immediate) {
            accessError(conversationId)?.let { throw it }
            val current = active!!
            if (aiCall) {
                val taskLimit = settingsStore.settingsFlow.value.browserTaskTimeoutMs
                    .coerceIn(MIN_TASK_TIMEOUT_MS, MAX_TASK_TIMEOUT_MS)
                taskWindow.check(current.conversationId, taskLimit)?.let { throw it }
            }
            val toolLimit = settingsStore.settingsFlow.value.browserToolTimeoutMs
                .coerceIn(MIN_TOOL_TIMEOUT_MS, MAX_TOOL_TIMEOUT_MS)
            val toolStartedAt = SystemClock.elapsedRealtime()
            val taskLimit = settingsStore.settingsFlow.value.browserTaskTimeoutMs
                .coerceIn(MIN_TASK_TIMEOUT_MS, MAX_TASK_TIMEOUT_MS)
            val actionLimit = if (aiCall) {
                minOf(toolLimit, taskWindow.remainingMs(current.conversationId, taskLimit))
            } else {
                toolLimit
            }
            val value = if (aiCall) {
                withTimeoutOrNull(actionLimit) { block(current) }
                    ?: run {
                        taskWindow.check(current.conversationId, taskLimit)?.let { throw it }
                        throw BrowserException("BROWSER_TIMEOUT", "The browser action exceeded its configured time limit.")
                    }
            } else {
                block(current)
            }
            if (aiCall) taskWindow.check(current.conversationId, taskLimit)?.let { throw it }
            if (aiCall && SystemClock.elapsedRealtime() - toolStartedAt > toolLimit) {
                taskWindow.check(current.conversationId, taskLimit)?.let { throw it }
                throw BrowserException("BROWSER_TIMEOUT", "The browser action exceeded its configured time limit.")
            }
            Result.success(value)
        }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }

    suspend fun open(url: String, conversationId: Uuid?, aiCall: Boolean = false): Result<Unit> = action(conversationId, aiCall) { current ->
        validateUrl(url)?.let { throw it }
        current.state.webView?.loadUrl(url.trim()) ?: throw noBrowser()
    }

    suspend fun readText(conversationId: Uuid?, aiCall: Boolean = false): Result<String> = action(conversationId, aiCall) { current ->
        val view = current.state.webView ?: throw noBrowser()
        val readable = ReadabilityRunner.extract(view)
        // A route may have been disposed while JS ran; never return its data elsewhere.
        ensureCurrent(current)
        readable?.takeIf { it.length >= 50 }
            ?: jsonObject(evaluate(current, BrowserJsScripts.readText)).string("text").orEmpty()
    }

    suspend fun selectedText(conversationId: Uuid?, aiCall: Boolean = false): Result<String> = action(conversationId, aiCall) { current ->
        jsonObject(evaluate(current, BrowserJsScripts.selection)).string("text").orEmpty()
    }

    suspend fun listClickables(max: Int = 60, conversationId: Uuid?, aiCall: Boolean = false): Result<List<BrowserClickable>> = action(conversationId, aiCall) { current ->
        val payload = evaluate(current, BrowserJsScripts.listClickables(max, Uuid.random().toString()))
        val array = decode(payload) as? JsonArray ?: throw invalidPage()
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            BrowserClickable(obj.string("id").orEmpty(), obj.string("tag").orEmpty(),
                obj.string("type").orEmpty(), obj.string("text").orEmpty()).takeIf { it.id.isNotBlank() }
        }
    }

    suspend fun click(id: String, conversationId: Uuid?, aiCall: Boolean = false): Result<Unit> = action(conversationId, aiCall) { current ->
        val info = inspect(current, id)
        BrowserHighRisk.forClick(info.tag, info.type, info.text)?.let { throw highRisk(it.kind.name.lowercase()) }
        ensureOk(evaluate(current, BrowserJsScripts.click(id)))
    }

    suspend fun typeInto(id: String, text: String, conversationId: Uuid?, aiCall: Boolean = false): Result<Unit> = action(conversationId, aiCall) { current ->
        if (text.length > 8_192) throw BrowserException("INPUT_TOO_LARGE", "Input is too long.")
        val info = inspect(current, id)
        BrowserHighRisk.forType(info.tag, info.type, info.text)?.let { throw highRisk(it.kind.name.lowercase()) }
        ensureOk(evaluate(current, BrowserJsScripts.typeInto(id, text)))
    }

    suspend fun scrollBy(dx: Int, dy: Int, conversationId: Uuid?, aiCall: Boolean = false): Result<Unit> = action(conversationId, aiCall) { current ->
        if (dx !in -5000..5000 || dy !in -5000..5000) throw BrowserException("INVALID_SCROLL", "Scroll distance is too large.")
        ensureOk(evaluate(current, BrowserJsScripts.scrollBy(dx, dy)))
    }

    suspend fun state(conversationId: Uuid?, aiCall: Boolean = false): Result<BrowserPageState> = action(conversationId, aiCall) { current ->
        val obj = jsonObject(evaluate(current, BrowserJsScripts.titleAndUrl))
        BrowserPageState(obj.string("title").orEmpty(), obj.string("url").orEmpty(),
            obj.string("loading") == "true", current.desktopMode)
    }

    suspend fun setDesktopMode(desktop: Boolean) = withContext(Dispatchers.Main.immediate) {
        val current = active ?: return@withContext
        val view = current.state.webView ?: return@withContext
        configureMode(view, desktop, current.mobileUserAgent, current.defaultUserAgent)
        active = current.copy(desktopMode = desktop)
        val target = current.state.currentUrl
        if (!target.isNullOrBlank()) view.loadUrl(target) else view.reload()
    }

    /** Starts or restarts an AI browser task after confirming the singleton is not owned elsewhere. */
    suspend fun beginTask(conversationId: Uuid?): BrowserException? = withContext(Dispatchers.Main.immediate) {
        if (conversationId == null) return@withContext BrowserException("NO_CONVERSATION", "The browser needs a conversation context.")
        val current = active
        if (current != null && current.conversationId != conversationId) {
            return@withContext BrowserException("BROWSER_BUSY", "A browser page is already open for another conversation.")
        }
        taskWindow.begin(conversationId)
    }

    /** Ending a task is allowed even after its time limit has expired. */
    suspend fun endTask(conversationId: Uuid?): BrowserException? = withContext(Dispatchers.Main.immediate) {
        accessError(conversationId)?.let { return@withContext it }
        taskWindow.end(conversationId!!)
    }

    private fun configureMode(view: WebView, desktop: Boolean, mobileUa: String, defaultUa: String) {
        view.settings.apply {
            userAgentString = if (desktop) BrowserUserAgent.desktop(defaultUa) else mobileUa
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        view.setInitialScale(0)
    }

    private suspend fun inspect(current: ActiveBrowser, id: String): BrowserClickable {
        if (id.isBlank()) throw BrowserException("INVALID_ID", "Action id is required.")
        val obj = jsonObject(evaluate(current, BrowserJsScripts.inspect(id)))
        if (obj["error"] != null) throw BrowserException("STALE_ID", "Action id is stale; list actions again.")
        return BrowserClickable(id, obj.string("tag").orEmpty(), obj.string("type").orEmpty(), obj.string("text").orEmpty())
    }

    private fun ensureCurrent(current: ActiveBrowser) {
        if (active?.state !== current.state) throw noBrowser()
    }

    private suspend fun evaluate(current: ActiveBrowser, script: String): String {
        ensureCurrent(current)
        val view = current.state.webView ?: throw noBrowser()
        val timeout = settingsStore.settingsFlow.value.browserToolTimeoutMs
            .coerceIn(MIN_TOOL_TIMEOUT_MS, MAX_TOOL_TIMEOUT_MS)
        val raw = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine<String> { continuation ->
                view.evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value) }
            }
        } ?: throw BrowserException("BROWSER_TIMEOUT", "The page did not respond. Wait for it to load, then try again.")
        ensureCurrent(current)
        return raw
    }

    private fun ensureOk(raw: String) {
        val obj = jsonObject(raw)
        obj.string("error")?.let {
            if (it == "high_risk") throw highRisk(obj.string("kind") ?: "unknown")
            if (it == "stale_id") throw BrowserException("STALE_ID", "Action id is stale; list actions again.")
            throw BrowserException("BROWSER_ACTION_FAILED", "The control cannot perform this action.")
        }
    }
    private fun jsonObject(raw: String) = decode(raw) as? JsonObject ?: throw invalidPage()
    private fun decode(raw: String): JsonElement {
        val outer = Json.parseToJsonElement(raw)
        return if (outer is JsonPrimitive && outer.isString) Json.parseToJsonElement(outer.content) else outer
    }
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun noBrowser() = BrowserException("NO_BROWSER", "No browser page is open. Call browser_open first (this asks the user for approval).")
    private fun invalidPage() = BrowserException("INVALID_PAGE", "The page did not return readable data. Try selecting text manually.")
    private fun highRisk(kind: String) = BrowserException("HIGH_RISK", "This action must be performed by the user (high risk: $kind). It was not executed; there is no AI override.")

    companion object {
        const val MIN_TOOL_TIMEOUT_MS = 10_000L
        const val MAX_TOOL_TIMEOUT_MS = 600_000L
        const val MIN_TASK_TIMEOUT_MS = 60_000L
        const val MAX_TASK_TIMEOUT_MS = 3_600_000L

        fun validateUrl(url: String): BrowserException? {
            val uri = runCatching { URI(url.trim()) }.getOrNull()
            return if (uri != null && uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) null
            else BrowserException("INVALID_URL", "Enter a valid http or https URL without embedded credentials.")
        }
    }
}

internal object BrowserDesktopViewport {
    const val SCRIPT = """
        (function () {
          var w = 1280;
          var head = document.head || document.documentElement;
          var m = document.querySelector('meta[name="viewport"]');
          if (!m) {
            m = document.createElement('meta');
            m.setAttribute('name', 'viewport');
            head.appendChild(m);
          }
          m.setAttribute('content', 'width=' + w + ', initial-scale=1');
          document.documentElement.style.width = w + 'px';
          document.documentElement.style.minWidth = w + 'px';
          return 'ok';
        })();
    """
}

/** Monotonic, conversation-owned timer for one AI-driven browser task. */
internal class BrowserTaskWindow(private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }) {
    private var owner: Uuid? = null
    private var taskStartedAtElapsed: Long? = null

    @Synchronized
    fun begin(conversationId: Uuid): BrowserException? {
        if (owner != null && owner != conversationId) return busy()
        owner = conversationId
        taskStartedAtElapsed = elapsedRealtime()
        return null
    }

    @Synchronized
    fun check(conversationId: Uuid, limitMs: Long): BrowserException? {
        if (owner != null && owner != conversationId) return busy()
        val now = elapsedRealtime()
        val startedAt = taskStartedAtElapsed ?: now.also {
            owner = conversationId
            taskStartedAtElapsed = it
        }
        return if (now - startedAt > limitMs) {
            BrowserException(
                "TASK_TIMEOUT",
                "This browser task exceeded the ${limitMs / 60_000}-minute limit. Ask the user to reopen the browser, then continue.",
            )
        } else null
    }

    @Synchronized
    fun remainingMs(conversationId: Uuid, limitMs: Long): Long {
        check(conversationId, limitMs)?.let { throw it }
        val startedAt = taskStartedAtElapsed ?: elapsedRealtime().also {
            owner = conversationId
            taskStartedAtElapsed = it
        }
        return (limitMs - (elapsedRealtime() - startedAt)).coerceAtLeast(1L)
    }

    @Synchronized
    fun end(conversationId: Uuid): BrowserException? {
        if (owner != null && owner != conversationId) return busy()
        reset()
        return null
    }

    @Synchronized
    fun onAttach(conversationId: Uuid, preserveAiTask: Boolean) {
        if (preserveAiTask && owner == conversationId && taskStartedAtElapsed != null) return
        reset()
    }

    @Synchronized
    fun reset() {
        owner = null
        taskStartedAtElapsed = null
    }

    @Synchronized
    fun onDetach() = reset()

    private fun busy() = BrowserException("BROWSER_BUSY", "This browser task belongs to another conversation.")
}

internal object BrowserUserAgent {
    private const val FALLBACK = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    fun desktop(current: String): String {
        val replaced = current.replace(Regex("\\(Linux; Android [^)]+\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "").replace("Version/4.0 ", "")
        return if (replaced.contains("X11; Linux x86_64") && replaced.contains("Chrome/")) replaced else FALLBACK
    }
}
