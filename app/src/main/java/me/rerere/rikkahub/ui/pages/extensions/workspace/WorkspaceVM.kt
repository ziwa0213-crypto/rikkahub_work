package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository

class WorkspaceVM(
    private val repository: WorkspaceRepository,
    private val terminalSessionManager: WorkspaceTerminalSessionManager,
) : ViewModel() {
    val workspaces = repository.listFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun create(name: String, owner: String? = null, onSaved: () -> Unit = {}) {
        save(onSaved) { repository.create(name, owner) }
    }

    fun setOwner(workspace: WorkspaceEntity, owner: String?, onSaved: () -> Unit = {}) {
        save(onSaved) { check(repository.setOwner(workspace.id, owner)) }
    }

    fun dismissSaveError() { _saveError.value = null }

    private fun save(onSaved: () -> Unit, action: suspend () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                action()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveError.value = e.message.orEmpty()
            } finally {
                _saving.value = false
            }
        }
    }

    fun rename(workspace: WorkspaceEntity, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(workspace.id, name) }
        }
    }

    fun delete(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            terminalSessionManager.closeWorkspace(workspace.root)
            repository.delete(workspace.id)
        }
    }
}
