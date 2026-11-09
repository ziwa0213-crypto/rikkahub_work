package me.rerere.rikkahub.service.pulse

import kotlinx.serialization.json.JsonElement
import me.rerere.ai.ui.UIMessagePart
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Scheduled runs bind side effects to a durable occurrence, not to the currently visible chat. */
class PulseToolContext(
    val execute: suspend (String, JsonElement, suspend () -> List<UIMessagePart>) -> List<UIMessagePart>,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PulseToolContext>
}
