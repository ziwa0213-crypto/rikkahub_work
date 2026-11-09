package me.rerere.rikkahub.service.pulse

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import java.security.MessageDigest

/** A durable receipt closes the retry window around non-idempotent external writes. */
internal class PulseEffects(
    private val load: (String) -> String?,
    private val save: (String, String) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun execute(run: String, name: String, args: JsonElement,
        block: suspend () -> List<UIMessagePart>): List<UIMessagePart> = mutex.withLock {
        val key = "effect:" + MessageDigest.getInstance("SHA-256")
            .digest("$run:$name:${canonical(args)}".toByteArray()).joinToString("") { "%02x".format(it) }
        val prior = load(key)
        check(prior != "PENDING") {
            "Previous write outcome is unknown; automatic repetition was blocked. Check the calendar or memory before retrying."
        }
        if (prior != null) return@withLock JsonInstant.decodeFromString<List<UIMessagePart>>(prior)
        save(key, "PENDING")
        block().also { save(key, JsonInstant.encodeToString(it)) }
    }

    private fun canonical(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(element.map(::canonical))
        else -> element
    }
}
