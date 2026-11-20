package me.rerere.llamacpp

enum class LoadStage { WEIGHTS, CONTEXT, PROJECTOR }

sealed interface LocalModelState {
    data object Idle : LocalModelState
    data class Loading(val modelId: String, val stage: LoadStage, val startedAt: Long) : LocalModelState
    data class Ready(
        val modelId: String, val contextSize: Int, val capabilities: LocalCapabilities,
        val projectorFailed: Boolean, val bytes: Long,
        val hasChatTemplate: Boolean = true,
        val gpuFallback: Boolean = false,
    ) : LocalModelState
    data class Failed(val modelId: String, val reason: LocalModelError, val detail: String? = null) : LocalModelState
}

class LocalModelNotLoadedException(val modelId: String) : IllegalStateException(
    "model not loaded: $modelId",
)
