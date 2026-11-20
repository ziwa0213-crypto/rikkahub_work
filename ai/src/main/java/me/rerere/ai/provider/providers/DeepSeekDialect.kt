package me.rerere.ai.provider.providers

/** Shared by all three DeepSeek-compatible request protocols. */
fun isDeepSeekDialect(host: String, modelId: String): Boolean =
    host.contains("deepseek", ignoreCase = true) || modelId.contains("deepseek", ignoreCase = true)
