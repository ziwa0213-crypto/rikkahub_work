package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.provider.ProviderSetting

/** Filter the retired provider before sealed-class decoding, for both backups and DataStore. */
object ProviderListCompatSerializer :
    JsonTransformingSerializer<List<ProviderSetting>>(ListSerializer(ProviderSetting.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val array = element as? JsonArray ?: return element
        return JsonArray(array.filterNot { item ->
            ((item as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "local_llamacpp"
        })
    }
}
