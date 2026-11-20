package me.rerere.llamacpp

/** Same native ownership seam used by the reference runtime, for JVM race tests. */
internal interface LocalRuntimeNative {
    fun loadModel(path: String, gpuLayers: Int): Long
    fun freeModel(model: Long)
    fun modelInfo(model: Long): String
    fun hasChatTemplate(model: Long): Boolean
    fun applyTemplate(model: Long, template: ByteArray?, request: ByteArray): ByteArray
    fun createContext(model: Long, nCtx: Int, nBatch: Int, nUBatch: Int, threads: Int, gpuLayers: Int): Long
    fun freeContext(engine: Long)
    fun contextSize(engine: Long): Int
    fun loadProjector(engine: Long, model: Long, path: String, threads: Int): IntArray
    fun cancel(engine: Long)
    fun beginMedia(engine: Long)
}

internal object RealLocalRuntimeNative : LocalRuntimeNative {
    override fun loadModel(path: String, gpuLayers: Int) = LlamaCppJni.nativeLoadModel(path, gpuLayers)
    override fun freeModel(model: Long) = LlamaCppJni.nativeFreeModel(model)
    override fun modelInfo(model: Long) = LlamaCppJni.nativeModelInfo(model)
    override fun hasChatTemplate(model: Long) = LlamaCppJni.nativeHasChatTemplate(model)
    override fun applyTemplate(model: Long, template: ByteArray?, request: ByteArray) =
        LlamaCppJni.nativeApplyTemplate(model, template, request)
    override fun createContext(model: Long, nCtx: Int, nBatch: Int, nUBatch: Int, threads: Int, gpuLayers: Int) =
        LlamaCppJni.nativeCreateContext(model, nCtx, nBatch, nUBatch, "f16", "f16", threads, gpuLayers)
    override fun freeContext(engine: Long) = LlamaCppJni.nativeFreeContext(engine)
    override fun contextSize(engine: Long) = LlamaCppJni.nativeContextSize(engine)
    override fun loadProjector(engine: Long, model: Long, path: String, threads: Int) =
        LlamaCppJni.nativeLoadProjector(engine, model, path, threads)
    override fun cancel(engine: Long) = LlamaCppJni.nativeCancelGeneration(engine)
    override fun beginMedia(engine: Long) = LlamaCppJni.nativeBeginMedia(engine)
}
