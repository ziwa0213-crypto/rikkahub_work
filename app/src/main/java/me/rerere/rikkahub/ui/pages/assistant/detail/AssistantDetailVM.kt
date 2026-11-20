package me.rerere.rikkahub.ui.pages.assistant.detail

import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.MemoryGroup
import me.rerere.rikkahub.data.model.RowMemory
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.repository.MemoryMigrationMode
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.memoryId
import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import kotlin.uuid.Uuid

private const val TAG = "AssistantDetailVM"

class AssistantDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant(id = assistantId)
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Assistant(id = assistantId)
        )

    val rowMemories = assistant.map { it.memoryId }.distinctUntilChanged()
        .flatMapLatest { memoryRepository.getRowMemoriesFlow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _memoryError = MutableStateFlow<String?>(null)
    val memoryError = _memoryError.asStateFlow()
    fun clearMemoryError() { _memoryError.value = null }

    private val _pendingMemoryMigration = MutableStateFlow<PendingMemoryMigration?>(null)
    val pendingMemoryMigration = _pendingMemoryMigration.asStateFlow()

    private fun memoryChange(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _memoryError.value = error.message ?: error.javaClass.simpleName }
        }
    }

    fun saveRowMemory(scope: String, row: RowMemory) = memoryChange {
        if (row.id == 0) memoryRepository.addRowMemory(scope, row.content)
        else memoryRepository.updateRowMemory(scope, row.id, row.content)
    }

    fun deleteRowMemories(scope: String, ids: List<Int>, onDeleted: () -> Unit = {}) = memoryChange {
        memoryRepository.deleteRowMemories(scope, ids)
        onDeleted()
    }

    fun selectMemoryScope(global: Boolean, groupId: Uuid?) = memoryChange {
        applyScopeChange(global, groupId)
    }

    /** Request a scope change and ask which row memories should follow it. */
    fun requestMemoryScopeChange(global: Boolean, groupId: Uuid?, targetLabel: String) = memoryChange {
        val current = assistant.value
        val fromScope = current.memoryId
        val toScope = when {
            global -> MemoryRepository.GLOBAL_MEMORY_ID
            groupId != null -> MemoryRepository.scopeOf(groupId)
            else -> current.id.toString()
        }
        if (fromScope == toScope) {
            applyScopeChange(global, groupId)
            return@memoryChange
        }
        val candidates = memoryRepository.getRowMemories(fromScope)
        if (candidates.isEmpty()) {
            applyScopeChange(global, groupId)
            return@memoryChange
        }
        val currentSettings = settingsStore.settingsFlow.first()
        _pendingMemoryMigration.value = PendingMemoryMigration(
            global = global,
            groupId = groupId,
            targetLabel = targetLabel,
            fromScope = fromScope,
            candidates = candidates,
            sharedUsers = currentSettings.assistants.count { it.memoryId == fromScope },
        )
    }

    fun confirmMemoryScopeChange(ids: Set<Int>, mode: MemoryMigrationMode) = memoryChange {
        val pending = _pendingMemoryMigration.value ?: return@memoryChange
        _pendingMemoryMigration.value = null
        if (ids.isNotEmpty()) {
            memoryRepository.migrateRowMemories(
                fromScope = pending.fromScope,
                toScope = targetScopeOf(pending),
                ids = ids.toList(),
                mode = mode,
            )
        }
        applyScopeChange(pending.global, pending.groupId)
    }

    fun dismissMemoryScopeChange() {
        _pendingMemoryMigration.value = null
    }

    private fun targetScopeOf(pending: PendingMemoryMigration): String = when {
        pending.global -> MemoryRepository.GLOBAL_MEMORY_ID
        pending.groupId != null -> MemoryRepository.scopeOf(pending.groupId)
        else -> assistantId.toString()
    }

    private suspend fun applyScopeChange(global: Boolean, groupId: Uuid?) {
        settingsStore.update { settings ->
            check(groupId == null || settings.memoryGroups.any { it.id == groupId }) { "Memory group no longer exists" }
            settings.copy(assistants = settings.assistants.map {
                if (it.id == assistantId) it.copy(useGlobalMemory = global, memoryGroupId = groupId) else it
            })
        }
    }

    fun createMemoryGroup(name: String) = memoryChange {
        val group = MemoryGroup(name = name.trim())
        require(group.name.isNotEmpty())
        settingsStore.update { settings -> settings.copy(
            memoryGroups = settings.memoryGroups + group,
            assistants = settings.assistants.map {
                if (it.id == assistantId) it.copy(useGlobalMemory = false, memoryGroupId = group.id) else it
            },
        ) }
    }

    fun renameMemoryGroup(group: MemoryGroup, name: String) = memoryChange {
        require(name.isNotBlank())
        settingsStore.update { settings -> settings.copy(memoryGroups = settings.memoryGroups.map {
            if (it.id == group.id) it.copy(name = name.trim()) else it
        }) }
    }

    private val _groupDeleteImpact = MutableStateFlow<MemoryGroupDeleteImpact?>(null)
    val groupDeleteImpact = _groupDeleteImpact.asStateFlow()
    fun dismissMemoryGroupDeletion() { _groupDeleteImpact.value = null }

    fun requestMemoryGroupDeletion(group: MemoryGroup) = memoryChange {
        val scope = Assistant(memoryGroupId = group.id).memoryId
        _groupDeleteImpact.value = MemoryGroupDeleteImpact(
            group, memoryRepository.getRowMemories(scope).size + memoryRepository.getFiles(scope).size,
            settings.value.assistants.count { it.memoryGroupId == group.id },
        )
    }

    fun confirmMemoryGroupDeletion() {
        val group = _groupDeleteImpact.value?.group ?: return
        _groupDeleteImpact.value = null
        memoryChange {
            val scope = Assistant(memoryGroupId = group.id).memoryId
            memoryRepository.deleteAllRowMemories(scope)
            memoryRepository.deleteFilesOfAssistant(scope)
            settingsStore.update { settings -> settings.copy(
                memoryGroups = settings.memoryGroups.filterNot { it.id == group.id },
                assistants = settings.assistants.map {
                    if (it.memoryGroupId == group.id) it.copy(useGlobalMemory = false, memoryGroupId = null) else it
                },
            ) }
        }
    }

    val memories = assistant
        .flatMapLatest { currentAssistant ->
            memoryRepository.getFilesFlow(memoryIdOf(currentAssistant))
        }
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val memoryConsolidation: StateFlow<MemoryConsolidationStatus> = assistant
        .flatMapLatest { currentAssistant ->
            memoryConsolidationScheduler.statusOf(memoryIdOf(currentAssistant))
        }
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = MemoryConsolidationStatus()
        )

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository
        .listFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            settingsStore.update { it.copy(assistantTags = tags) }
            update(
                assistant.value.copy(
                    tags = tagIds.toList()
                )
            )
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            cleanupUnusedTags()
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val validTagIds = settings.assistantTags.map { it.id }.toSet()

                // 清理 assistant 中的无效 tag id
                val cleanedAssistants = settings.assistants.map { assistant ->
                    val validTags = assistant.tags.filter { tagId ->
                        validTagIds.contains(tagId)
                    }
                    if (validTags.size != assistant.tags.size) {
                        assistant.copy(tags = validTags)
                    } else {
                        assistant
                    }
                }

                // 获取清理后的 assistant 中使用的 tag id
                val usedTagIds = cleanedAssistants.flatMap { it.tags }.toSet()

                // 清理未使用的 tags
                val cleanedTags = settings.assistantTags.filter { tag ->
                    usedTagIds.contains(tag.id)
                }

                // 检查是否需要更新
                val needUpdateAssistants = cleanedAssistants != settings.assistants
                val needUpdateTags = cleanedTags.size != settings.assistantTags.size

                if (needUpdateAssistants || needUpdateTags) {
                    settings.copy(
                        assistants = cleanedAssistants,
                        assistantTags = cleanedTags
                    )
                } else {
                    settings
                }
            }
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            var replaced: Assistant? = null
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            replaced = it
                            assistant
                        } else {
                            it
                        }
                    })
            }
            replaced?.let { old ->
                checkAvatarDelete(old = old, new = assistant) // 删除旧头像
                checkBackgroundDelete(old = old, new = assistant) // 删除旧背景
            }
        }
    }

    // 助手还没加载出来时 assistant 是个占位对象，记忆库要按页面拿到的 assistantId 来定位
    private fun memoryIdOf(assistant: Assistant) = assistant.memoryId

    /** 当前助手读写的记忆库 */
    val memoryId: String get() = memoryIdOf(assistant.value)

    /** 整理所有待整理的对话，并把整个记忆库清理一遍 */
    fun organizeMemory() {
        memoryConsolidationScheduler.organize(memoryId)
    }

    fun deleteMemory(memory: MemoryFile) {
        viewModelScope.launch {
            memoryRepository.deleteFile(memoryIdOf(assistant.value), memory.path)
        }
    }

    fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            filesManager.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    fun checkBackgroundDelete(old: Assistant, new: Assistant) {
        val oldBackground = old.background
        val newBackground = new.background

        if (oldBackground != null && oldBackground != newBackground) {
            try {
                val oldUri = oldBackground.toUri()
                if (oldUri.scheme == "content" || oldUri.scheme == "file") {
                    filesManager.deleteChatFiles(listOf(oldUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete background file: $oldBackground", e)
            }
        }
    }
}

data class MemoryGroupDeleteImpact(val group: MemoryGroup, val memoryCount: Int, val memberCount: Int)

data class PendingMemoryMigration(
    val global: Boolean,
    val groupId: Uuid?,
    val targetLabel: String,
    val fromScope: String,
    val candidates: List<RowMemory>,
    val sharedUsers: Int,
)
