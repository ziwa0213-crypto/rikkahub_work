package me.rerere.llamacpp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.localModelData by preferencesDataStore("local_llamacpp")

/** Kept separate from serializable Model; filenames are stable provider model IDs. */
class LocalModelStore(context: Context) {
    private val data = context.applicationContext.localModelData
    private val installed = stringPreferencesKey("installed_llamacpp")
    private val projectors = stringPreferencesKey("mmproj_llamacpp")
    private val loadingPath = stringPreferencesKey("loading_model_path")
    private val loadingAt = longPreferencesKey("loading_started_at")

    suspend fun models(): Map<String, String> = decode(data.data.first()[installed])
    suspend fun projectors(): Map<String, String> = decode(data.data.first()[projectors])
    suspend fun install(name: String, path: String, projector: String?) {
        data.edit { p ->
            p[installed] = Json.encodeToString(decode(p[installed]) + (name to path))
            if (projector != null) p[projectors] = Json.encodeToString(decode(p[projectors]) + (name to projector))
        }
    }
    suspend fun remove(name: String) {
        data.edit { p ->
            p[installed] = Json.encodeToString(decode(p[installed]) - name)
            p[projectors] = Json.encodeToString(decode(p[projectors]) - name)
        }
    }
    suspend fun markLoading(path: String) {
        data.edit { it[loadingPath] = path; it[loadingAt] = System.currentTimeMillis() }
    }
    suspend fun clearLoading() { data.edit { it.remove(loadingPath); it.remove(loadingAt) } }
    suspend fun interrupted(): String? {
        val p = data.data.first()
        val age = System.currentTimeMillis() - (p[loadingAt] ?: 0)
        return if (age in 0..1_800_000) p[loadingPath] else { clearLoading(); null }
    }
    private fun decode(value: String?): Map<String, String> = value?.let {
        runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
    }.orEmpty()
}
