package me.rerere.llamacpp

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.json.JSONObject
import org.json.JSONArray

data class ContextPlan(val nCtx: Int, val kvBytes: Long, val estimatedBytes: Long, val tight: Boolean = false)

enum class LocalModelError { MEMORY, TOO_LONG, BAD_FILE, DOWNLOAD, NOT_INSTALLED, NO_SPACE, EMPTY_OUTPUT, GPU_LOST }
class LocalModelException(val reason: LocalModelError, val detail: String? = null) :
    RuntimeException(reason.name + (detail?.let { " ($it)" } ?: ""))

object ContextPlanner {
    const val DEFAULT_MAX_TOKENS = 2048
    private const val MB = 1024L * 1024

    fun plan(info: String, fileBytes: Long, available: Long, userLimit: Int = 16384, cushionBytes: Long = 0): ContextPlan {
        val o = JSONObject(info)
        val layers = o.optInt("n_layers").coerceAtLeast(1)
        val headsRaw = o.opt("n_head_kv")
        val heads = when (headsRaw) {
            is JSONArray -> (0 until headsRaw.length()).map { headsRaw.optInt(it) }
                .filter { it > 0 }.maxOrNull() ?: 1
            else -> o.optInt("n_head_kv")
        }.coerceAtLeast(1)
        val key = o.optInt("n_embd_head_k").takeIf { it > 0 } ?: o.optInt("n_embd").coerceAtLeast(128)
        val value = o.optInt("n_embd_head_v").takeIf { it > 0 } ?: key
        // F16 K and V; grouped-query models use KV heads, never query heads.
        val perToken = layers.toLong() * heads * (key + value) * 2
        // mmap weights are reclaimable; estimate hot pages only, for warnings.
        val memoryTokens = ((available * .55 + cushionBytes.coerceIn(0, 1024 * MB) - fileBytes / 4 - 512 * MB) / perToken).toInt().coerceAtLeast(0)
        val hardCap = minOf(o.optInt("n_ctx_train", 4096), userLimit, 16384).coerceAtLeast(512)
        val context = hardCap / 256 * 256
        val kv = perToken * context
        return ContextPlan(context, kv, fileBytes / 4 + kv + 512 * MB, tight = memoryTokens < context)
    }

    fun outputBudget(contextSize: Int, maxTokens: Int?): Int =
        (maxTokens ?: DEFAULT_MAX_TOKENS).coerceIn(1, maxOf(64, contextSize / 4))

    /** Keep complete user turns, including every assistant call/result between them. */
    fun trim(messages: List<UIMessage>, budget: Int, count: (List<UIMessage>) -> Int): List<UIMessage> {
        var current = messages
        while (count(current) > budget) {
            val users = current.indices.filter { current[it].role == MessageRole.USER }
            if (users.size <= 1) throw LocalModelException(LocalModelError.TOO_LONG)
            val end = users[1]
            current = current.filterIndexed { index, msg -> index >= end || msg.role == MessageRole.SYSTEM }
        }
        return current
    }
}
