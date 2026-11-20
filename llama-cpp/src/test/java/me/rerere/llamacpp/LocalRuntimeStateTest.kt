package me.rerere.llamacpp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.ui.StreamChunk
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class LocalRuntimeStateTest {
    @get:Rule val files = TemporaryFolder()
    private data class ContextCall(val nBatch: Int, val nUBatch: Int, val gpuLayers: Int)

    private class Native : LocalRuntimeNative {
        var loads = 0; var frees = 0; var size = 0
        var fail = false; var failProjector = false; var zeroContext = false
        var observe: () -> Unit = {}
        var templatePresent = true
        var templateOverride: String? = null
        override fun hasChatTemplate(model: Long) = templatePresent
        override fun applyTemplate(model: Long, template: ByteArray?, request: ByteArray): ByteArray {
            templateOverride = template?.toString(Charsets.UTF_8)
            return request
        }
        val layers = mutableListOf<Int>()
        val contextCalls = mutableListOf<ContextCall>()
        val modelPaths = mutableListOf<String>()
        val projectorPaths = mutableListOf<String>()
        var mediaReset: () -> Unit = {}
        var failGpu = false
        var failGpuContext = false
        var cancelGpu = false
        override fun loadModel(path: String, gpuLayers: Int): Long {
            observe(); loads++; layers += gpuLayers; modelPaths += path
            if (cancelGpu && gpuLayers > 0) throw kotlinx.coroutines.CancellationException("cancel load")
            if (failGpu && gpuLayers > 0) error("GPU driver failed")
            return 1
        }
        override fun freeModel(model: Long) { frees++ }
        override fun modelInfo(model: Long) = """{"n_layers":32,"n_head_kv":8,"n_embd_head_k":128,"n_embd_head_v":128,"n_ctx_train":16384}"""
        override fun createContext(model: Long, nCtx: Int, nBatch: Int, nUBatch: Int, threads: Int, gpuLayers: Int): Long {
            observe()
            contextCalls += ContextCall(nBatch, nUBatch, gpuLayers)
            if (fail || (failGpuContext && gpuLayers > 0)) error("alloc failed")
            size = nCtx
            return if (zeroContext) 0 else 2
        }
        override fun freeContext(engine: Long) { frees++ }
        override fun contextSize(engine: Long) = size
        override fun loadProjector(engine: Long, model: Long, path: String, threads: Int): IntArray {
            observe(); projectorPaths += path
            if (failProjector) error("bad projector")
            return intArrayOf(1, 0, -1)
        }
        override fun cancel(engine: Long) = Unit
        override fun beginMedia(engine: Long) = mediaReset()
    }

    private fun runtime(
        native: Native,
        available: Long = 32L shl 30,
        total: Long = 32L shl 30,
        log: (String) -> Unit = {},
        clear: () -> Unit = {},
    ) = LlamaCppRuntime(native, { available }, {}, { clear() }, { 100L }, log, { 0L }, { total })

    @Test fun `unloaded inference refuses without loading native code`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        assertTrue(runCatching { runtime.withReady("m") {} }.exceptionOrNull() is LocalModelNotLoadedException)
        assertEquals(0, native.loads)
    }

    @Test fun `GPU success uses 99 layers and changing setting reloads on CPU`() = runBlocking {
        val native = Native(); val runtime = runtime(native); val file = files.newFile()
        runtime.load("m", file.path, null, 4096, useGpu = true)
        assertTrue(runtime.gpuActive)
        assertFalse((runtime.state.value as LocalModelState.Ready).gpuFallback)
        runtime.load("m", file.path, null, 4096, useGpu = true)
        assertEquals(listOf(99), native.layers)
        runtime.load("m", file.path, null, 4096, useGpu = false)
        assertEquals(listOf(99, 0), native.layers)
        assertEquals(listOf(ContextCall(512, 128, 99), ContextCall(512, 512, 0)), native.contextCalls)
        assertFalse(runtime.gpuActive)
        assertFalse((runtime.state.value as LocalModelState.Ready).gpuFallback)
    }

    @Test fun `GPU load or context failure falls back once and frees partial resources`() = runBlocking {
        for (contextFailure in listOf(false, true)) {
            val native = Native().apply { failGpu = !contextFailure; failGpuContext = contextFailure }
            val runtime = runtime(native); val file = files.newFile()
            runtime.load("m", file.path, null, 4096, useGpu = true)
            assertEquals(listOf(99, 0), native.layers)
            assertTrue((runtime.state.value as LocalModelState.Ready).gpuFallback)
            assertFalse(runtime.gpuActive)
            assertEquals(if (contextFailure) 1 else 0, native.frees)
            runtime.load("m", file.path, null, 4096, useGpu = true)
            assertEquals(listOf(99, 0), native.layers)
            runtime.unload()
            runtime.load("m", file.path, null, 4096, useGpu = true)
            assertEquals(listOf(99, 0, 99, 0), native.layers)
        }
    }

    @Test fun `CPU failure never retries and GPU plus CPU failure stops after two attempts`() = runBlocking {
        for (useGpu in listOf(false, true)) {
            val native = Native().apply { fail = true }; val runtime = runtime(native)
            runtime.load("m", files.newFile().path, null, 4096, useGpu = useGpu)
            assertEquals(if (useGpu) listOf(99, 0) else listOf(0), native.layers)
            assertEquals(LocalModelError.MEMORY, (runtime.state.value as LocalModelState.Failed).reason)
            assertEquals(if (useGpu) 2 else 1, native.frees)
        }
    }

    @Test fun `GPU cancellation propagates without CPU fallback`() = runBlocking {
        val native = Native().apply { cancelGpu = true }; val runtime = runtime(native)
        val error = runCatching { runtime.load("m", files.newFile().path, null, 4096, useGpu = true) }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(listOf(99), native.layers)
        assertEquals(LocalModelState.Idle, runtime.state.value)
    }

    @Test fun `recognizes device loss without mistaking allocation failure or cancellation for it`() {
        val nativeLoss = RuntimeException("vk::Queue::submit: ErrorDeviceLost")
        assertTrue(isVulkanDeviceLost(nativeLoss))
        assertTrue(isVulkanDeviceLost(RuntimeException("VK_ERROR_DEVICE_LOST")))
        assertTrue(isVulkanDeviceLost(IllegalStateException("decode failed", nativeLoss)))
        assertFalse(isVulkanDeviceLost(OutOfMemoryError("alloc failed")))
        assertFalse(isVulkanDeviceLost(CancellationException("stopped: VK_ERROR_DEVICE_LOST")))
        assertFalse(isVulkanDeviceLost(IllegalStateException("wrapper", CancellationException("VK_ERROR_DEVICE_LOST"))))
        assertFalse(isVulkanDeviceLost(IllegalStateException("decode failed")))
    }

    @Test fun `device loss before a token retries the same request once and streams a CPU reply`() = runBlocking {
        val logs = mutableListOf<String>()
        val native = Native(); val runtime = runtime(native, log = { logs += it })
        val weights = files.newFile().path; val projector = files.newFile().path
        runtime.load("m", weights, projector, 8192, memoryFusion = true, useGpu = true)
        val attempts = mutableListOf<Boolean>()
        val chunks = mutableListOf<StreamChunk>()
        val produced = AtomicBoolean()
        withTimeout(5000) {
            withLocalGpuRecovery(runtime, produced::get) {
                runtime.withReady("m") {
                    val useGpu = gpuActive
                    attempts += useGpu
                    streamFromLoadedModel(object : LocalGeneration {
                        override fun generate(onPiece: (String) -> Boolean) {
                            if (useGpu) error("vk::Queue::submit: ErrorDeviceLost")
                            produced.set(true)
                            onPiece("CPU reply")
                        }
                        override fun parse(text: String, partial: Boolean) = JSONObject().put("content", text).toString()
                        override fun cancel() = Unit
                    }, "local").collect { chunks += it }
                }
            }
        }
        assertEquals(listOf(true, false), attempts)
        assertEquals("CPU reply", chunks.filterIsInstance<StreamChunk.TextDelta>().single().text)
        assertEquals(1, chunks.count { it is StreamChunk.Finish })
        assertEquals(listOf(weights, weights), native.modelPaths)
        assertEquals(listOf(projector, projector), native.projectorPaths)
        assertEquals(listOf(ContextCall(512, 128, 99), ContextCall(512, 512, 0)), native.contextCalls)
        assertEquals(2, logs.count { it.contains("Fusion: enabled=true") })
        assertTrue(logs.any { it.contains("Vulkan device lost before any output, retrying on CPU") })
        val ready = runtime.state.value as LocalModelState.Ready
        assertEquals(8192, ready.contextSize)
        assertTrue(ready.gpuFallback)
        assertFalse(runtime.gpuActive)
        assertEquals(0, native.frees)

        runtime.load("m", weights, projector, 8192, memoryFusion = true, useGpu = true)
        assertEquals(listOf(99, 0), native.layers)
        runtime.load("next", files.newFile().path, null, 4096, useGpu = true)
        assertEquals(listOf(99, 0, 0), native.layers)
        assertTrue(logs.any { it.startsWith("GPU disabled for this process") })
        assertEquals(2, native.frees)
        val restarted = runtime(Native())
        restarted.load("m", weights, null, 4096, useGpu = true)
        assertTrue(restarted.gpuActive)
    }

    @Test fun `device loss after output leaves partial reply and does not retry`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096, useGpu = true)
        native.mediaReset = { error("lost context must not be touched again") }
        val original = RuntimeException("VK_ERROR_DEVICE_LOST")
        val output = mutableListOf<String>()
        var attempts = 0
        val error = runCatching {
            withLocalGpuRecovery(runtime, { output.isNotEmpty() }) {
                runtime.withReady("m") { attempts++; output += "partial reply"; throw original }
            }
        }.exceptionOrNull() as LocalModelException
        assertEquals(1, attempts)
        assertEquals(listOf("partial reply"), output)
        assertEquals(LocalModelError.GPU_LOST, error.reason)
        assertSame(original, error.cause)
        assertFalse(runtime.gpuActive)
        assertFalse(runtime.isGenerating)
        assertEquals(listOf(99), native.layers)
        assertEquals(0, native.frees)
        assertEquals(LocalModelState.Failed("m", LocalModelError.GPU_LOST), runtime.state.value)
    }

    @Test fun `second generation failure surfaces the localized loss without another attempt`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096, useGpu = true)
        val cpuFailure = IllegalStateException("CPU failed")
        var attempts = 0
        val error = runCatching {
            withLocalGpuRecovery(runtime, { false }) {
                runtime.withReady("m") {
                    attempts++
                    if (gpuActive) error("VK_ERROR_DEVICE_LOST")
                    throw cpuFailure
                }
            }
        }.exceptionOrNull() as LocalModelException
        assertEquals(2, attempts)
        assertEquals(listOf(99, 0), native.layers)
        assertEquals(LocalModelError.GPU_LOST, error.reason)
        val suppressed = error.suppressedExceptions.single()
        assertEquals(cpuFailure.javaClass, suppressed.javaClass)
        assertEquals(cpuFailure.message, suppressed.message)
    }

    @Test fun `pending unload wins over recovery and does not free the lost device`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096, useGpu = true)
        val error = runCatching {
            withLocalGpuRecovery(runtime, { false }) {
                runtime.withReady("m") {
                    assertFalse(requestUnloadWhenIdle())
                    error("VK_ERROR_DEVICE_LOST")
                }
            }
        }.exceptionOrNull() as LocalModelException
        assertEquals(LocalModelError.GPU_LOST, error.reason)
        assertEquals(LocalModelState.Idle, runtime.state.value)
        assertEquals(listOf(99), native.layers)
        assertEquals(0, native.frees)
    }

    @Test fun `cancellation and ordinary failures stay on original GPU path`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096, useGpu = true)
        val cleanup = IllegalStateException("secondary cleanup failure")
        for (failure in listOf(CancellationException("VK_ERROR_DEVICE_LOST"), IllegalStateException("ordinary"))) {
            native.mediaReset = { throw cleanup }
            val error = runCatching {
                withLocalGpuRecovery(runtime, { false }) { runtime.withReady("m") { throw failure } }
            }.exceptionOrNull()
            // Debug stack-trace recovery may copy an exception crossing withContext.
            assertEquals(failure.javaClass, error?.javaClass)
            assertEquals(failure.message, error?.message)
            assertSame(cleanup, failure.suppressedExceptions.single())
            assertTrue(runtime.gpuActive)
        }
        assertEquals(listOf(99), native.layers)
    }

    @Test fun `projector can load using half total RAM when available is low`() = runBlocking {
        val logs = mutableListOf<String>()
        val native = Native()
        val runtime = runtime(native, available = 3L shl 30, total = 16L shl 30, log = { logs += it })
        val projector = files.newFile()
        RandomAccessFile(projector, "rw").use { it.setLength(2L shl 30) }
        runtime.load("m", files.newFile().path, projector.path, 8192)
        val ready = runtime.state.value as LocalModelState.Ready
        assertTrue(ready.capabilities.vision)
        assertFalse(ready.projectorFailed)
        assertEquals(8192, ready.contextSize)
        assertTrue(logs.any { it.contains("available=3072MB total=16384MB") })
    }

    @Test fun `load stages are observable and identical ready load is a no op`() = runBlocking {
        val native = Native(); var cleared = 0; val runtime = runtime(native) { cleared++ }
        val stages = mutableListOf<LoadStage>()
        native.observe = { stages += (runtime.state.value as LocalModelState.Loading).stage }
        val file = files.newFile(); val projector = files.newFile()
        runtime.load("m", file.path, projector.path, 8192)
        assertEquals(listOf(LoadStage.WEIGHTS, LoadStage.CONTEXT, LoadStage.PROJECTOR), stages)
        val ready = runtime.state.value as LocalModelState.Ready
        assertEquals(8192, ready.contextSize); assertTrue(ready.capabilities.vision)
        runtime.load("m", file.path, projector.path, 8192)
        assertEquals(1, native.loads); assertEquals(1, cleared)
        runtime.unload(); assertEquals(LocalModelState.Idle, runtime.state.value)
        assertEquals(2, native.frees)
    }

    @Test fun `failed context frees weights and publishes MEMORY without throwing`() = runBlocking {
        val native = Native().apply { fail = true }; var cleared = false
        val runtime = runtime(native) { cleared = true }
        runtime.load("m", files.newFile().path, null, 4096)
        assertEquals(LocalModelError.MEMORY, (runtime.state.value as LocalModelState.Failed).reason)
        assertEquals(1, native.frees); assertTrue(cleared)
    }

    @Test fun `projector failure retains text ready state`() = runBlocking {
        val native = Native().apply { failProjector = true }; val runtime = runtime(native)
        runtime.load("m", files.newFile().path, files.newFile().path, 4096)
        assertTrue((runtime.state.value as LocalModelState.Ready).projectorFailed)
        assertFalse((runtime.state.value as LocalModelState.Ready).capabilities.vision)
    }

    @Test fun `zero context handle reports allocation failure with selected window`() = runBlocking {
        val native = Native().apply { zeroContext = true }; val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096)
        val failed = runtime.state.value as LocalModelState.Failed
        assertEquals(LocalModelError.MEMORY, failed.reason)
        assertTrue(failed.detail.orEmpty().contains("nCtx=4096"))
        assertEquals(1, native.frees)
    }

    @Test fun `queued unload runs in finally even when inference fails`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096)
        runCatching { runtime.withReady("m") {
            assertFalse(requestUnloadWhenIdle()); assertEquals(0, native.frees)
            error("inference failed")
        } }
        assertEquals(LocalModelState.Idle, runtime.state.value)
        assertFalse(runtime.isGenerating); assertEquals(2, native.frees)
    }

    @Test fun `cancelled inference consumes queued unload`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096)
        val entered = CompletableDeferred<Unit>()
        val job = async { runtime.withReady("m") { entered.complete(Unit); awaitCancellation() } }
        entered.await(); assertFalse(runtime.requestUnloadWhenIdle())
        assertEquals(0, native.frees)
        job.cancelAndJoin()
        assertEquals(LocalModelState.Idle, runtime.state.value); assertEquals(2, native.frees)
    }

    @Test fun `returning to foreground cancels deferred unload`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096)
        runtime.withReady("m") { requestUnloadWhenIdle(); cancelPendingUnload() }
        assertTrue(runtime.state.value is LocalModelState.Ready); assertEquals(0, native.frees)
    }

    @Test fun `missing file publishes not installed without touching native`() = runBlocking {
        val native = Native(); val runtime = runtime(native)
        runtime.load("gone", "/missing/model.gguf", null, 4096)
        assertEquals(LocalModelError.NOT_INSTALLED, (runtime.state.value as LocalModelState.Failed).reason)
        assertEquals(0, native.loads)
    }

    @Test fun `missing template warns but loads and custom unicode template reaches native`() = runBlocking {
        val native = Native().apply { templatePresent = false }; val runtime = runtime(native)
        runtime.load("m", files.newFile().path, null, 4096)
        assertFalse((runtime.state.value as LocalModelState.Ready).hasChatTemplate)
        runtime.withReady("m") {
            applyTemplate("{}", "{{ '世界🌍' }}")
            assertEquals("{{ '世界🌍' }}", native.templateOverride)
            applyTemplate("{}", null)
            assertNull(native.templateOverride)
        }
    }
}
