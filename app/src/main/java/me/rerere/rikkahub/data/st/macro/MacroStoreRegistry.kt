package me.rerere.rikkahub.data.st.macro

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.rerere.rikkahub.data.datastore.SettingsStore

/** One instance per scope, including the lock protecting compound reads and writes. */
internal class PersistedVariableStore(
    initial: Map<String, String>,
    private val scopeId: String,
    private val writer: ScopeVariableWriter,
) : MacroVariableStore {
    private val lock = Any()
    private val inner = InMemoryMacroVariableStore().apply { restore(initial) }
    private var published = inner.snapshot()

    override fun get(name: String) = synchronized(lock) { inner.get(name) }
    override fun has(name: String) = synchronized(lock) { inner.has(name) }
    override fun getAtKey(name: String, key: String) = synchronized(lock) { inner.getAtKey(name, key) }
    override fun set(name: String, value: String) = mutate { inner.set(name, value) }
    override fun del(name: String) = mutate { inner.del(name) }
    override fun inc(name: String) = mutate { inner.inc(name) }
    override fun dec(name: String) = mutate { inner.dec(name) }
    override fun add(name: String, value: String) = mutate { inner.add(name, value) }
    override fun setAtKey(name: String, key: String, value: String) = mutate { inner.setAtKey(name, key, value) }

    private fun <T> mutate(action: () -> T): T = synchronized(lock) {
        val result = action()
        val values = inner.snapshot()
        if (values != published) {
            published = values
            writer.offer(scopeId, values)
        }
        result
    }
}

internal class MacroStoreRegistry(private val writer: ScopeVariableWriter) {
    private val lock = Any()
    private val stores = mutableMapOf<String, PersistedVariableStore>()

    fun store(scopeId: String, initial: () -> Map<String, String>): PersistedVariableStore = synchronized(lock) {
        stores.getOrPut(scopeId) { PersistedVariableStore(initial(), scopeId, writer) }
    }

    suspend fun flushAtTurnEnd(scopes: Set<String>? = null): Result<Unit> = writer.flushAndJoin(scopes)
}

/** Owned by the application, independent of a view model or generation job. */
class CharacterMacroStorage(settingsStore: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = ScopeVariableWriter(scope) { id, values ->
        settingsStore.update { current -> current.copy(stVariables = current.stVariables + (id to values)) }
    }
    internal val registry = MacroStoreRegistry(writer)
}
