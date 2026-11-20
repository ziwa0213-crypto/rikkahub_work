package me.rerere.rikkahub.ui.pages.setting.locallm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppCatalogEntry
import me.rerere.llamacpp.LlamaCppInstall
import me.rerere.llamacpp.LlamaCppRuntime
import me.rerere.llamacpp.LlamaCppCatalog
import me.rerere.llamacpp.LocalModelState
import me.rerere.llamacpp.MemoryGate
import me.rerere.llamacpp.MemoryVerdict
import me.rerere.llamacpp.PendingLocalImport
import me.rerere.rikkahub.data.datastore.SettingsStore

/** Process-scoped singleton: chat capsules and model sheets share load confirmation. */
class SettingLocalLlmViewModel(val installer: LlamaCppInstall, private val settings: SettingsStore,
    private val runtime: LlamaCppRuntime) : ViewModel() {
    val runtimeState = runtime.state
    private val _lowMemory = MutableStateFlow<MemoryVerdict?>(null)
    val lowMemoryConfirm = _lowMemory.asStateFlow()
    private var pendingLoad: String? = null
    private var loading: Job? = null

    fun load(modelId: String, allowLowMemory: Boolean = false) {
        if (loading?.isActive == true || runtime.state.value is LocalModelState.Loading) return
        val entry = LlamaCppCatalog.ENTRIES.find { it.filename == modelId }
        if (entry != null && !allowLowMemory) {
            val verdict = installer.memoryVerdict(entry)
            if (verdict.gate == MemoryGate.WARN) {
                pendingLoad = modelId; _lowMemory.value = verdict
                return
            }
        }
        _lowMemory.value = null
        pendingLoad = null
        loading = viewModelScope.launch {
            try {
            settings.awaitLoaded()
            installer.repair()
            val installed = installer.models.value.find { it.name == modelId }
            if (installed == null) { runtime.notInstalled(modelId); return@launch }
            val current = settings.settingsFlow.value
            val model = current.providers.filterIsInstance<ProviderSetting.LlamaCppLocal>()
                .flatMap { it.models }.find { it.modelId == modelId }
            runtime.load(modelId, installed.path, installed.projector, model?.contextLength ?: 16384,
                memoryFusion = current.enableMemoryFusion, useGpu = current.enableLocalGpu)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
        }
    }
    fun confirmLowMemory() { pendingLoad?.let { load(it, allowLowMemory = true) } }
    fun dismissLowMemory() { pendingLoad = null; _lowMemory.value = null }
    fun unload() { viewModelScope.launch { if (runtime.requestUnloadWhenIdle()) runtime.unload() } }
    private val _error = MutableStateFlow<Throwable?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _pending = MutableStateFlow<PendingLocalImport?>(null)
    val pending = _pending.asStateFlow()
    private val _architectureWarning = MutableStateFlow(false)
    val architectureWarning = _architectureWarning.asStateFlow()
    private var approval: CompletableDeferred<Boolean>? = null
    private var operation: Job? = null
    private var associated: String? = null

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { _error.value = e }
            finally { _busy.value = false; _architectureWarning.value = false; approval = null }
        }
    }

    fun install(entry: LlamaCppCatalogEntry) = run {
        settings.awaitLoaded()
        val base = settings.settingsFlow.value.providers.filterIsInstance<ProviderSetting.LlamaCppLocal>().first().downloadBase
        installer.download(entry, base) {
            val deferred = CompletableDeferred<Boolean>()
            approval = deferred; _architectureWarning.value = true
            try { deferred.await() } finally { _architectureWarning.value = false; approval = null }
        }
    }
    fun approveDownload(yes: Boolean) { approval?.complete(yes) }
    fun import(uri: Uri, model: String? = null) = run {
        associated = model
        val value = installer.prepareImport(uri)
        _pending.value = value
        if (!value.needsWarning && (!value.projector || model != null)) commitPending(model)
    }
    fun commit(model: String? = associated) = run { commitPending(model) }
    private suspend fun commitPending(model: String?) {
        val value = _pending.value ?: return
        installer.commitImport(value, model)
        _pending.value = null; associated = null
    }
    fun discard() {
        _pending.value?.let(installer::discard)
        _pending.value = null; associated = null
    }
    fun delete(name: String) = run { installer.delete(name) }
    fun cancel() { operation?.cancel() }
    fun dismissError() { _error.value = null }
    override fun onCleared() { discard(); super.onCleared() }
}
