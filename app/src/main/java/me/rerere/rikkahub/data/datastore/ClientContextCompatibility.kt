@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.Assistant

/** Missing fields mean legacy data; constructing new configs still enables the feature. */
object PulseClientContextSerializer : JsonTransformingSerializer<PulseConfig>(PulseConfig.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonObject && "enableClientContext" !in element)
            JsonObject(element + ("enableClientContext" to JsonPrimitive(false))) else element
}

object AssistantClientContextSerializer : JsonTransformingSerializer<Assistant>(Assistant.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonObject && "localTools" !in element) JsonObject(element + ("localTools" to buildJsonArray {
            add(buildJsonObject { put("type", "time_info") })
        })) else element
}
