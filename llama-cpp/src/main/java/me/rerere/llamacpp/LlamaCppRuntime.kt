package me.rerere.llamacpp

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

data class LocalCapabilities(val vision: Boolean = false, val audio: Boolean = false, val sampleRate: Int = -1)

/** One lock covers load, template, inference and deletion. Native cancellation alone
 * may run concurrently; freeing shares its smaller monitor to avoid use-after-free. */
class LlamaCppRuntime internal constructor(
    private val native: LocalRuntimeNative,
    private val availableMemory: () -> Long,
    private val markLoading: suspend (String) -> Unit,
    private val clearLoading: suspend () -> Unit,
    private val elapsedRealtime: () -> Long,
    private val log: (String) -> Unit,
    private val swapTotal: () -> Long,
    private val totalMemory: () -> Long,
) {
    constructor(context: Context, store: LocalModelStore) : this(RealLocalRuntimeNative, {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        info.availMem
    }, store::markLoading, store::clearLoading, SystemClock::elapsedRealtime,
        { Log.d("LocalModel", it) }, LocalMemoryGate::swapTotalBytes,
        { LocalMemoryGate.deviceTotalBytes(context) })

    private data class LoadRequest(
        val modelId: String,
        val path: String,
        val projector: String?,
        val limit: Int,
        val memoryFusion: Boolean,
        val useGpu: Boolean,
    )

    private val mutex = Mutex()
    private val cancelFree = Any()
    private var model = 0L
    @Volatile private var engine = 0L
    private var lastRequest: LoadRequest? = null
    private var gpuLoss: LocalModelException? = null
    private var gpuDisabledForProcess = false
    @Volatile private var gpuLoaded = false
    @Volatile private var activeOwner: String? = null
    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Idle)
    val state = _state.asStateFlow()
    val gpuActive: Boolean get() = gpuLoaded && state.value is LocalModelState.Ready
    @Volatile private var generating = 0
    private val _generating = MutableStateFlow(false)
    val generatingState = _generating.asStateFlow()
    @Volatile private var pendingUnload = false
    val isGenerating: Boolean get() = generating > 0
    val hasPendingUnload: Boolean get() = pendingUnload
    var capabilities = LocalCapabilities(); private set
    var projectorFailed = false; private set
    var contextSize = 0; private set
    private val threads get() = localInferenceThreads(Runtime.getRuntime().availableProcessors())

    /** Explicit loads and the one CPU recovery share the inference lock. */
    suspend fun <T> withReady(modelId: String, owner: String = modelId, block: suspend LlamaCppRuntime.() -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock {
            val ready = state.value as? LocalModelState.Ready
            if (ready?.modelId != modelId) throw LocalModelNotLoadedException(modelId)
            synchronized(cancelFree) { activeOwner = owner; generating++; _generating.value = true }
            var failure: Throwable? = null
            try {
                block()
            } catch (error: Throwable) {
                val reported = if (isVulkanDeviceLost(error)) {
                    val loss = LocalModelException(LocalModelError.GPU_LOST).apply { initCause(error) }
                    if (gpuActive) retireLostGpuLocked(modelId, loss)
                    loss
                } else error
                failure = reported
                throw reported
            } finally {
                try {
                    if (engine != 0L) beginMedia()
                } catch (cleanup: Throwable) {
                    val original = failure ?: throw cleanup
                    if (cleanup !== original) original.addSuppressed(cleanup)
                } finally {
                    synchronized(cancelFree) {
                        activeOwner = null
                        generating--
                        _generating.value = generating > 0
                        if (generating == 0 && pendingUnload) {
                            pendingUnload = false
                            unloadLocked()
                        }
                    }
                }
            }
        } }

    suspend fun load(modelId: String, path: String, projector: String?, limit: Int, memoryFusion: Boolean = false, useGpu: Boolean = false) =
        withContext(Dispatchers.IO) { mutex.withLock {
            loadLocked(LoadRequest(modelId, path, projector, limit, memoryFusion, useGpu))
        } }

    private suspend fun loadLocked(request: LoadRequest, gpuFallback: Boolean = false) {
        val (modelId, path, mmproj, limit, memoryFusion, useGpu) = request
        val ready = state.value as? LocalModelState.Ready
        val previous = lastRequest
        if (ready?.modelId == modelId && previous?.path == path && previous.projector == mmproj &&
            contextSize <= limit && previous.useGpu == useGpu) return
        unloadLocked()
        val wantGpu = useGpu && !gpuDisabledForProcess && !gpuFallback
        if (useGpu && gpuDisabledForProcess) log("GPU disabled for this process; using CPU")
        val startedAt = elapsedRealtime()
        fun stage(value: LoadStage) { _state.value = LocalModelState.Loading(modelId, value, startedAt) }
        val available = availableMemory()
        val total = totalMemory()
        suspend fun loadOnce(gpuLayers: Int, fellBack: Boolean) {
            stage(LoadStage.WEIGHTS)
            currentCoroutineContext().ensureActive()
            model = native.loadModel(path, gpuLayers)
            check(model != 0L)
            currentCoroutineContext().ensureActive()
            stage(LoadStage.CONTEXT)
            val swap = swapTotal()
            val cushion = LocalMemoryGate.fusionCushionBytes(memoryFusion, swap)
            log("Fusion: enabled=$memoryFusion swap=${swap / 1048576}MB cushion=${cushion / 1048576}MB")
            val info = JSONObject(native.modelInfo(model))
            GgufModelInfo.kvHeads(File(path))?.let { info.put("n_head_kv", JSONArray(it)) }
            val plan = ContextPlanner.plan(info.toString(), File(path).length(), available, limit, cushion)
            log("Context: nCtx=${plan.nCtx} limit=$limit kvBytes=${plan.kvBytes} estimatedBytes=${plan.estimatedBytes} available=${available / 1048576}MB total=${total / 1048576}MB tight=${plan.tight} threads=$threads")
            engine = native.createContext(model, plan.nCtx, nBatch = 512,
                nUBatch = if (gpuLayers > 0) 128 else 512, threads = threads, gpuLayers = gpuLayers)
            if (engine == 0L) throw LocalModelException(LocalModelError.MEMORY, "nCtx=${plan.nCtx}")
            currentCoroutineContext().ensureActive()
            contextSize = native.contextSize(engine)
            if (mmproj != null) {
                stage(LoadStage.PROJECTOR)
                try {
                    val usable = maxOf(availableMemory(), total / 2)
                    val file = File(mmproj)
                    if (!file.isFile || file.length() + 256L * 1024 * 1024 > usable * .55)
                        throw LocalModelException(LocalModelError.MEMORY)
                    val caps = native.loadProjector(engine, model, mmproj, threads)
                    capabilities = LocalCapabilities(caps[0] != 0, caps[1] != 0, caps[2])
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { projectorFailed = true }
            }
            val loaded = LocalModelState.Ready(modelId, contextSize, capabilities, projectorFailed,
                File(path).length() + (mmproj?.let { File(it).length() } ?: 0), native.hasChatTemplate(model), fellBack)
            lastRequest = request
            gpuLoaded = gpuLayers > 0
            _state.value = loaded
        }
        // An interrupted marker cannot catch SIGSEGV or establish incompatibility.
        // It is only a retry hint on next launch and never disables a model.
        try {
            if (!File(path).isFile) throw LocalModelException(LocalModelError.NOT_INSTALLED)
            markLoading(path)
            if (!wantGpu) loadOnce(0, gpuFallback || useGpu)
            else try { loadOnce(99, false) }
            catch (e: CancellationException) { throw e }
            catch (t: Throwable) {
                unloadLocked()
                currentCoroutineContext().ensureActive()
                log("GPU load failed (${t.javaClass.simpleName}), falling back to CPU")
                loadOnce(0, true)
            }
        } catch (t: Throwable) {
            unloadLocked()
            if (t is CancellationException) throw t
            val reason = (t as? LocalModelException)?.reason ?: if (t is OutOfMemoryError || t.message?.contains("alloc", true) == true) LocalModelError.MEMORY else LocalModelError.BAD_FILE
            _state.value = LocalModelState.Failed(modelId, reason, t.message)
        }
        finally { withContext(NonCancellable) { clearLoading() } }
    }

    private fun retireLostGpuLocked(modelId: String, loss: LocalModelException) {
        gpuDisabledForProcess = true
        gpuLoss = loss
        // llama_free synchronizes the Vulkan queue in a noexcept destructor at this pin.
        // After device loss it can terminate the process. Drop access to both handles and
        // let process exit reclaim this single model/context pair.
        synchronized(cancelFree) { engine = 0L }
        model = 0L
        gpuLoaded = false
        contextSize = 0
        capabilities = LocalCapabilities()
        projectorFailed = false
        _state.value = LocalModelState.Failed(modelId, LocalModelError.GPU_LOST)
        log("Vulkan device lost; GPU disabled for this process: ${loss.cause?.message}")
    }

    /** Called after withReady releases the lock. A newer load or user unload takes precedence. */
    internal suspend fun reloadOnCpu(loss: LocalModelException): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (gpuLoss !== loss) return@withLock false
            val request = lastRequest ?: return@withLock false
            currentCoroutineContext().ensureActive()
            log("Vulkan device lost before any output, retrying on CPU")
            loadLocked(request, gpuFallback = true)
            when (val result = state.value) {
                is LocalModelState.Ready -> true
                is LocalModelState.Failed -> {
                    log("CPU reload failed after device loss: ${result.reason}")
                    loss.addSuppressed(LocalModelException(result.reason, result.detail))
                    false
                }
                else -> false
            }
        }
    }

    suspend fun notInstalled(modelId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        unloadLocked()
        _state.value = LocalModelState.Failed(modelId, LocalModelError.NOT_INSTALLED)
    } }

    suspend fun unload() = withContext(Dispatchers.IO) { mutex.withLock { unloadLocked() } }
    suspend fun unloadIf(predicate: () -> Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock { if (predicate()) unloadLocked() }
    }

    fun requestUnloadWhenIdle(): Boolean = synchronized(cancelFree) {
        if (generating > 0) { pendingUnload = true; false } else true
    }

    fun cancelPendingUnload() = synchronized(cancelFree) { pendingUnload = false }

    suspend fun delete(path: String, action: suspend () -> Unit) = withContext(Dispatchers.IO) {
        // Queue behind active inference: deleting another model must not cancel it.
        mutex.withLock { if (lastRequest?.path == path) unloadLocked(); action() }
    }

    private fun unloadLocked() {
        synchronized(cancelFree) {
            if (engine != 0L) native.freeContext(engine)
            engine = 0
        }
        if (model != 0L) native.freeModel(model)
        model = 0; lastRequest = null; gpuLoss = null; contextSize = 0; gpuLoaded = false
        capabilities = LocalCapabilities(); projectorFailed = false
        pendingUnload = false
        _state.value = LocalModelState.Idle
    }
    fun cancelGeneration(owner: String) = synchronized(cancelFree) {
        if (engine != 0L && activeOwner == owner) native.cancel(engine)
    }
    fun beginMedia() = native.beginMedia(engine)
    fun image(bytes: ByteArray, width: Int, height: Int) = LlamaCppJni.nativeAddImage(engine, bytes, width, height)
    fun audio(pcm: FloatArray) = LlamaCppJni.nativeAddAudio(engine, pcm)
    fun video(frames: Array<ByteArray>, width: Int, height: Int) = LlamaCppJni.nativeAddVideo(engine, frames, width, height)
    fun applyTemplate(request: String, template: String? = null): ByteArray {
        val override = template?.takeIf { it.isNotBlank() }
        log("Template: ${override?.take(80) ?: "automatic"}")
        return native.applyTemplate(model, override?.toByteArray(Charsets.UTF_8), request.toByteArray(Charsets.UTF_8))
    }
    fun release(applied: ByteArray) = LlamaCppJni.nativeReleaseApplied(applied)
    fun countApplied(applied: ByteArray) = LlamaCppJni.nativeCountApplied(engine, model, applied)
    fun countTokens(text: String) = LlamaCppJni.nativeCountTokens(model, text.toByteArray())
    fun parse(text: String, partial: Boolean, applied: ByteArray) =
        LlamaCppJni.nativeParseChat(text.toByteArray(), partial, applied).toString(Charsets.UTF_8)
    fun generate(applied: ByteArray, max: Int, onPiece: (String) -> Boolean) = LlamaCppJni.nativeGenerate(
        engine, model, applied, max, object : LlamaCppJni.TokenSink {
            override fun onToken(pieceUtf8: ByteArray) = onPiece(pieceUtf8.toString(Charsets.UTF_8))
        })
}

internal fun localInferenceThreads(cores: Int): Int = cores.coerceIn(2, 6)
