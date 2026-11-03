package me.rerere.workspace.rootfs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(
        val builtin: List<RootfsEntry>,
        val all: List<RootfsEntry>,
        val fromCache: Boolean,
        val fetchedAt: Long,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
    ) : CatalogState
    data class Failed(val message: String, val cached: List<RootfsEntry> = emptyList()) : CatalogState
}

class RootfsCatalog(private val client: RootfsIndexClient, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val current = state.value as? CatalogState.Ready
            val cache = client.readCache()
            if (cache != null) publish(cache, refreshing = true)
            else if (current != null) mutableState.value = current.copy(refreshing = true)
            else mutableState.value = CatalogState.Loading
            // Display the cache first, then refresh it without blanking the list (including fresh caches).
            client.load(forceRefresh = forceRefresh || cache != null).fold(
                onSuccess = { publish(it, offline = it.fromCache) },
                onFailure = {
                    if (current != null) mutableState.value = current.copy(refreshing = false, offline = true)
                    else mutableState.value = CatalogState.Failed("Rootfs index unavailable")
                },
            )
        }
    }

    fun byId(id: RootfsId): List<RootfsEntry> = all().filter { it.id == id }
    fun tiersOf(id: RootfsId, arch: RootfsArch): List<RootfsEntry> = byId(id).filter { it.arch == arch }.sortedBy { it.tier.ordinal }
    fun builtinEntries(): List<RootfsEntry> = (state.value as? CatalogState.Ready)?.builtin.orEmpty()
    private fun all(): List<RootfsEntry> = when (val value = state.value) {
        is CatalogState.Ready -> value.all
        is CatalogState.Failed -> value.cached
        CatalogState.Loading -> emptyList()
    }

    private fun publish(loaded: RootfsIndexClient.Loaded, refreshing: Boolean = false, offline: Boolean = false) {
        val all = loaded.index.entries().sortedWith(compareBy<RootfsEntry> { it.id.distro }.thenByDescending { it.id.version }.thenBy { it.arch.ordinal }.thenBy { it.tier.ordinal })
        val builtin = BuiltinRootfs.ids.flatMap { id -> RootfsArch.entries.mapNotNull { arch ->
            RootfsTier.preference.firstNotNullOfOrNull { tier -> all.firstOrNull { it.id == id && it.arch == arch && it.tier == tier } }
        } }
        mutableState.value = CatalogState.Ready(builtin, all, loaded.fromCache, loaded.fetchedAt, refreshing, offline)
    }
}
