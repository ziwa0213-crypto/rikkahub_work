package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.WorkspaceOwnerScope
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import kotlin.uuid.Uuid

internal object CharacterWorkspaceProvisioner {
    const val COMMAND = "bash /workspace/.rikkahub/install-deps.sh"
    private val workspaceLock = Mutex()

    suspend fun ensureWorkspace(repo: WorkspaceRepository, ownerId: Uuid, configuredId: Uuid?): WorkspaceEntity =
        workspaceLock.withLock {
            configuredId?.let { repo.getById(it.toString()) }?.takeIf(WorkspaceOwnerScope.CHARACTER::includes)
                ?: run {
                    // Include the role ID so opening several role editors cannot share their files.
                    val name = "Character-$ownerId"
                    repo.listFlow().first().firstOrNull { it.name == name && WorkspaceOwnerScope.CHARACTER.includes(it) }
                        ?: repo.create(name, WorkspaceOwnerScope.CHARACTER.value)
                }
        }

    suspend fun provision(context: Context, repo: WorkspaceRepository, workspace: WorkspaceEntity) {
        require(WorkspaceOwnerScope.CHARACTER.includes(workspace))
        val templates = mapOf(
            "README.md" to R.string.character_workspace_readme,
            "NOTES.md" to R.string.character_workspace_notes,
            "WORLD.md" to R.string.character_workspace_world,
            "ARCHIVE.md" to R.string.character_workspace_archive,
            "TOOLS.md" to R.string.character_workspace_tools,
        )
        val existing = repo.listFiles(workspace.id, WorkspaceStorageArea.FILES, "").mapTo(hashSetOf()) { it.name }
        for ((path, res) in templates) if (path !in existing) writeAbsent(repo, workspace.id, path, context.getString(res))
    }

    suspend fun prepareInstall(context: Context, repo: WorkspaceRepository, id: String) = withContext(Dispatchers.IO) {
        val workspace = checkNotNull(repo.getById(id))
        require(WorkspaceOwnerScope.CHARACTER.includes(workspace) && workspace.shellStatus == WorkspaceShellStatus.READY.name)
        for (name in listOf("install-deps.sh", "requirements.txt")) {
            val content = context.assets.open("character/$name").bufferedReader().use { it.readText() }
            // Use a dedicated app-owned directory; never interpolate user input into the terminal command.
            repo.writeText(id, ".rikkahub/$name", content, overwrite = true)
        }
    }

    private suspend fun writeAbsent(repo: WorkspaceRepository, id: String, path: String, content: String) {
        try {
            repo.writeText(id, path, content, overwrite = false)
        } catch (failure: IllegalArgumentException) {
            if (failure.message?.contains("already exists", ignoreCase = true) != true) throw failure
        }
    }
}
