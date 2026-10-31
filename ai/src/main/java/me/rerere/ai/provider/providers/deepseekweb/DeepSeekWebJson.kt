package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.util.json

/** Returns an object only when the value is actually a JSON object. */
internal fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject

/**
 * Parses a DeepSeek REST response and surfaces its business error before callers
 * try to read nested data. The response body is deliberately not included in
 * parse errors because it may contain credentials or server-side request data.
 */
internal fun parseDeepSeekResponse(body: String): JsonObject {
    val root = runCatching { json.parseToJsonElement(body) }
        .getOrNull()
        .objOrNull()
        ?: error("DeepSeek response is not a JSON object")

    root.responseCode("code")?.takeIf { it != 0 }?.let { code ->
        error("DeepSeek API error $code: ${root.errorMessage()}")
    }

    root["data"].objOrNull()?.let { data ->
        data.responseCode("biz_code")?.takeIf { it != 0 }?.let { code ->
            error("DeepSeek API error $code: ${data.errorMessage()}")
        }
    }
    return root
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.responseCode(key: String): Int? {
    val value = this[key]
    if (value == null || value == JsonNull) return null
    return (value as? JsonPrimitive)?.intOrNull ?: error("DeepSeek response has an invalid error code")
}

private fun JsonObject.errorMessage(): String {
    val raw = listOf("msg", "biz_msg", "message")
        .firstNotNullOfOrNull { this[it].stringOrNull()?.takeIf(String::isNotBlank) }
        ?: return "request failed"
    // Do not partially redact Cookie headers: they can contain many values and
    // spaces. Omit the whole detail when the server mentions credentials.
    if (CREDENTIAL_MARKER.containsMatchIn(raw) || OPAQUE_CREDENTIAL.containsMatchIn(raw)) {
        return "request failed (sensitive details omitted)"
    }
    return raw.take(300)
}

private val CREDENTIAL_MARKER = Regex(
    "(?i)cookie|token|authorization|bearer|session[_-]?id|api[_-]?key|secret|password"
)
private val OPAQUE_CREDENTIAL = Regex("[A-Za-z0-9_+/.=-]{40,}")
