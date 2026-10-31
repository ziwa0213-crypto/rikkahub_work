package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.SshWorkspaceDraft
import me.rerere.rikkahub.data.model.SshWorkspaceSettings
import me.rerere.rikkahub.data.model.normalizeGroupData
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SecretCipher
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBackend
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.workspace.WorkspaceType
import me.rerere.workspace.ssh.SshAuthType
import me.rerere.workspace.ssh.SshConnectionConfig
import me.rerere.workspace.ssh.SshConnectionPool
import me.rerere.workspace.ssh.SshProbeResult
import me.rerere.workspace.ssh.SshShell
import me.rerere.workspace.ssh.SshWorkspaceBackend
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.uuid.Uuid

/** Repository facade for local Rootfs-backed and remote SSH workspaces. */
class WorkspaceRepository private constructor(
    private val dao: WorkspaceDAO,
    private val manager: WorkspaceManager,
    private val rootfsInstaller: RootfsInstaller,
    private val updateSettings: suspend ((Settings) -> Settings) -> Unit,
    private val sshPool: SshConnectionPool?,
) {
    internal constructor(
        dao: WorkspaceDAO,
        manager: WorkspaceManager,
        rootfsInstaller: RootfsInstaller,
        settingsStore: SettingsStore,
        sshPool: SshConnectionPool,
    ) : this(
        dao,
        manager,
        rootfsInstaller,
        { transform -> settingsStore.update(transform) },
        sshPool,
    )

    constructor(
        dao: WorkspaceDAO,
        manager: WorkspaceManager,
        rootfsInstaller: RootfsInstaller,
        updateSettings: suspend ((Settings) -> Settings) -> Unit,
    ) : this(dao, manager, rootfsInstaller, updateSettings, null)

    // Installation/deletion and local file operations must not race with a session teardown.
    private val lifecycleMutex = Mutex()

    fun listFlow(): Flow<List<WorkspaceEntity>> = dao.listFlow()

    suspend fun checkIntegrity() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            var workspaces = dao.getAll()
            val stale = workspaces.filter { it.sharedRootfsFrom != null && it.capabilities().localFiles }
            if (stale.isNotEmpty()) {
                val now = System.currentTimeMillis()
                stale.forEach { workspace ->
                    dao.upsert(
                        workspace.copy(
                            sharedRootfsFrom = null,
                            shellStatus = WorkspaceShellStatus.DISABLED.name,
                            updatedAt = now,
                        )
                    )
                }
                workspaces = dao.getAll()
            }
            updateSettings { settings ->
                settings.normalizeGroupData(workspaces.map { it.id }.toSet())
            }
            for (workspace in workspaces) {
                if (!workspace.capabilities().localFiles) continue
                val dir = manager.workspaceDir(workspace.root)
                if (!dir.exists()) {
                    Log.w(TAG, "Workspace directory missing, marking as broken: id=${workspace.id}, root=${workspace.root}")
                    if (workspace.shellStatus != WorkspaceShellStatus.BROKEN.name) {
                        updateShellState(workspace.id, WorkspaceShellStatus.BROKEN.name)
                    }
                    continue
                }
                if ((workspace.shellStatus == WorkspaceShellStatus.READY.name ||
                        workspace.shellStatus == WorkspaceShellStatus.INSTALLING.name) &&
                    !manager.hasRootfs(workspace.root)
                ) {
                    Log.w(TAG, "Rootfs missing, resetting shell status: id=${workspace.id}")
                    updateShellState(workspace.id, WorkspaceShellStatus.DISABLED.name)
                }
            }
        }
    }

    suspend fun getById(id: String): WorkspaceEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun getByRoot(root: String): WorkspaceEntity? = withContext(Dispatchers.IO) {
        dao.getAll().firstOrNull { it.root == root }
    }

    /** Prepare a local terminal while keeping deletion/rootfs replacement serialized. */
    internal suspend fun prepareTerminalEnvironment(root: String, prepare: () -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            lifecycleMutex.withLock {
                val workspace = dao.getAll().firstOrNull { it.root == root } ?: return@withLock false
                if (!workspace.capabilities().localFiles ||
                    workspace.shellStatus != WorkspaceShellStatus.READY.name ||
                    !manager.hasRootfs(root)
                ) return@withLock false
                manager.ensureWorkspace(root)
                prepare()
                true
            }
        }

    suspend fun cleanupTempDirs() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            dao.getAll().filter { it.capabilities().localFiles }.forEach { workspace ->
                manager.tempDir(workspace.root).let { if (it.exists()) it.deleteRecursively() }
                manager.linuxDir(workspace.root).resolve("tmp").let { if (it.exists()) it.deleteRecursively() }
                manager.linuxDir(workspace.root).resolve("var/tmp").let { if (it.exists()) it.deleteRecursively() }
            }
        }
    }

    suspend fun create(name: String, owner: String? = null): WorkspaceEntity =
        withContext(Dispatchers.IO) {
            lifecycleMutex.withLock {
                require(owner == null || owner == "chat" || owner == "group") { "Invalid workspace owner" }
                val finalName = name.trim().ifBlank { "Workspace" }
                require(!isNameTaken(finalName, excludeId = null)) {
                    "Workspace name already exists: $finalName"
                }
                val id = Uuid.random().toString()
                val now = System.currentTimeMillis()
                val workspace = WorkspaceEntity(
                    id = id,
                    name = finalName,
                    root = id,
                    createdAt = now,
                    updatedAt = now,
                    lastAccessAt = null,
                    owner = owner,
                    shellStatus = WorkspaceShellStatus.DISABLED.name,
                )
                manager.ensureWorkspace(workspace.root)
                dao.upsert(workspace)
                workspace
            }
        }

    /** Test an SSH draft without persisting it. */
    suspend fun probeSsh(id: String?, draft: SshWorkspaceDraft): SshProbeResult {
        val pool = sshPool ?: error("SSH workspace support is unavailable")
        val previous = id?.let { dao.getById(it)?.sshSettings() }
        return runInterruptible(Dispatchers.IO) {
            pool.probe(draft.toSettings(previous).toConnectionConfig())
        }
    }

    /** Create or update an SSH workspace. Credentials are encrypted before persistence. */
    suspend fun saveSsh(id: String?, draft: SshWorkspaceDraft): WorkspaceEntity =
        lifecycleMutex.withLock {
            val existing = id?.let { dao.getById(it) ?: error("Workspace not found: $it") }
            require(existing == null || existing.isSsh()) { "Not an SSH workspace: $id" }
            val finalName = draft.name.trim().ifBlank { "Workspace" }
            require(!isNameTaken(finalName, excludeId = id)) {
                "Workspace name already exists: $finalName"
            }
            val settings = draft.toSettings(existing?.sshSettings())
            val now = System.currentTimeMillis()
            val workspace = existing?.copy(
                name = finalName,
                sshConfig = JsonInstant.encodeToString(settings),
                shellStatus = WorkspaceShellStatus.READY.name,
                updatedAt = now,
            ) ?: Uuid.random().toString().let { newId ->
                WorkspaceEntity(
                    id = newId,
                    name = finalName,
                    root = newId,
                    shellStatus = WorkspaceShellStatus.READY.name,
                    createdAt = now,
                    updatedAt = now,
                    type = WorkspaceType.SSH.name,
                    sshConfig = JsonInstant.encodeToString(settings),
                )
            }
            dao.upsert(workspace)
            sshPool?.close(workspace.id)
            workspace
        }

    suspend fun rename(id: String, name: String): Boolean = lifecycleMutex.withLock {
        val workspace = dao.getById(id) ?: return@withLock false
        val finalName = name.trim().ifBlank { workspace.name }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        dao.upsert(workspace.copy(name = finalName, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun isNameTaken(name: String, excludeId: String?): Boolean {
        val target = name.trim()
        return dao.getAll().any { it.id != excludeId && it.name.trim() == target }
    }

    suspend fun setShellCompatibilityMode(id: String, enabled: Boolean) {
        dao.setShellCompatibilityMode(id, enabled, System.currentTimeMillis())
    }

    suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
        val workspace = dao.getById(id) ?: return false
        val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
        dao.upsert(
            workspace.copy(
                toolApprovals = JsonInstant.encodeToString(overrides),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun setOwner(id: String, owner: String?): Boolean = lifecycleMutex.withLock {
        require(owner == null || owner == "chat" || owner == "group") { "Invalid workspace owner" }
        val workspace = dao.getById(id) ?: return@withLock false
        dao.upsert(workspace.copy(owner = owner, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun installRootfs(
        id: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean = lifecycleMutex.withLock {
        val workspace = dao.getById(id) ?: return@withLock false
        require(workspace.capabilities().rootfs) { "Rootfs is not available for ${workspace.type} workspaces" }
        updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
        try {
            runInterruptible(Dispatchers.IO) {
                rootfsInstaller.install(workspace.root, url, onProgress)
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            true
        } catch (e: CancellationException) {
            withContext(NonCancellable) { restoreShellState(workspace) }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) { restoreShellState(workspace) }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installRootfs failed: workspace=${workspace.id}, root=${workspace.root}, url=$url", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        }
    }

    suspend fun layout(id: String): WorkspaceLayout? = dao.getById(id)?.let { workspace ->
        if (workspace.isSsh()) {
            workspace.sshSettings()?.let { SshWorkspaceBackend.layoutOf(it.remoteDir) }
        } else {
            manager.layout
        }
    }

    suspend fun listFiles(id: String, area: WorkspaceStorageArea, path: String): List<WorkspaceFileEntry> =
        readFromBackend(id) { it.listFiles(area, path) }

    suspend fun readText(id: String, path: String): String = readFromBackend(id) { it.readText(path) }

    suspend fun writeText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withBackend(id) { it.writeText(path, text, overwrite) }

    suspend fun readTextForPreview(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): String = readFromBackend(id) { backend ->
        when (area) {
            WorkspaceStorageArea.FILES -> backend.readText(path)
            WorkspaceStorageArea.LINUX -> {
                val size = backend.fileSize(area, path)
                require(size <= MAX_PREVIEW_BYTES) { "文件过大, 无法预览 ($size bytes)" }
                ByteArrayOutputStream().use { output ->
                    backend.exportFile(area, path, output)
                    output.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    suspend fun importFile(
        id: String,
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry = withBackend(id) {
        it.importFile(area, destinationPath, fileName, inputStream)
    }

    suspend fun fileSize(id: String, area: WorkspaceStorageArea, path: String): Long =
        readFromBackend(id) { it.fileSize(area, path) }

    suspend fun resolveFile(id: String, area: WorkspaceStorageArea, path: String): File? =
        withContext(Dispatchers.IO) {
            val workspace = dao.getById(id) ?: return@withContext null
            runCatching { backendOf(workspace).localFile(area, path) }.getOrNull()?.takeIf { it.isFile }
        }

    suspend fun exportFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        outputStream: OutputStream,
    ) = readFromBackend(id) { it.exportFile(area, path, outputStream) }

    suspend fun rootfsFileSize(id: String, path: String): Long = absoluteFileSize(id, path)

    suspend fun resolveRootfsFile(id: String, path: String): File? = resolveAbsoluteFile(id, path)

    suspend fun rootfsShell(id: String): String = shell(id)

    suspend fun exportRootfsFile(id: String, path: String, outputStream: OutputStream) =
        exportAbsoluteFile(id, path, outputStream)

    suspend fun absoluteFileSize(id: String, path: String): Long = readFromBackend(id) { it.absoluteFileSize(path) }

    suspend fun resolveAbsoluteFile(id: String, path: String): File? = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext null
        runCatching { backendOf(workspace).localAbsoluteFile(path) }.getOrNull()?.takeIf { it.isFile }
    }

    suspend fun shell(id: String): String = readFromBackend(id) { it.shell() }

    suspend fun writeAbsoluteText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withBackend(id) { it.writeAbsoluteText(path, text, overwrite) }

    suspend fun exportAbsoluteFile(id: String, path: String, outputStream: OutputStream) =
        readFromBackend(id) { it.exportAbsoluteFile(path, outputStream) }

    suspend fun deleteFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        recursive: Boolean,
    ): Boolean = withBackend(id) { it.deleteFile(area, path, recursive) }

    suspend fun moveFile(
        id: String,
        source: String,
        target: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = lifecycleMutex.withLock {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        require(!workspace.isSsh()) { "Moving files is not supported by remote workspaces" }
        withContext(Dispatchers.IO) {
            manager.ensureWorkspace(workspace.root)
            manager.moveFile(workspace.root, source, target, overwrite)
        }
    }

    suspend fun executeCommand(
        id: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
    ): WorkspaceCommandResult = readFromBackend(id) {
        it.executeCommand(command, cwd, timeoutMillis, stdin)
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val workspace = dao.getById(id) ?: return@withLock false
            withContext(NonCancellable) {
                dao.deleteById(id)
                sshPool?.close(id)
                if (workspace.capabilities().localFiles) manager.deleteWorkspace(workspace.root)
                cleanupAssistantReferences(id)
            }
            true
        }
    }

    fun openSshShell(workspace: WorkspaceEntity, columns: Int, rows: Int): SshShell {
        val backend = backendOf(workspace) as? SshWorkspaceBackend
            ?: error("Not an SSH workspace: ${workspace.id}")
        return backend.openShell(columns, rows)
    }

    private suspend fun cleanupAssistantReferences(workspaceId: String) {
        updateSettings { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.workspaceId?.toString() == workspaceId) assistant.copy(workspaceId = null) else assistant
                },
                groups = settings.groups.map { group ->
                    if (group.workspaceId?.toString() == workspaceId) group.copy(workspaceId = null) else group
                },
            )
        }
    }

    private fun backendOf(workspace: WorkspaceEntity): WorkspaceBackend =
        if (workspace.isSsh()) {
            val pool = sshPool ?: error("SSH workspace support is unavailable")
            val settings = workspace.sshSettings() ?: error("SSH workspace config is missing: ${workspace.id}")
            SshWorkspaceBackend(pool, workspace.id, settings.toConnectionConfig())
        } else {
            manager.backend(workspace.root, workspace.shellCompatibilityMode)
        }

    private fun SshWorkspaceSettings.toConnectionConfig() = SshConnectionConfig(
        host = host,
        port = port,
        username = username,
        authType = authType,
        password = encryptedPassword?.let(::decryptSecret),
        privateKey = encryptedPrivateKey?.let(::decryptSecret),
        passphrase = encryptedPassphrase?.let(::decryptSecret),
        remoteDir = remoteDir,
        hostKey = hostKey,
    )

    private fun SshWorkspaceDraft.toSettings(previous: SshWorkspaceSettings?): SshWorkspaceSettings {
        val normalizedRemoteDir = remoteDir.trim().trimEnd('/').ifEmpty { "/" }
        require(normalizedRemoteDir.startsWith("/")) { "Remote directory must be an absolute path" }
        require(host.isNotBlank()) { "Host is required" }
        require(username.isNotBlank()) { "Username is required" }
        require(port in 1..65535) { "Invalid port: $port" }
        val keepPrivateKey = privateKey.isBlank()
        return SshWorkspaceSettings(
            host = host.trim(),
            port = port,
            username = username.trim(),
            authType = authType,
            remoteDir = normalizedRemoteDir,
            hostKey = hostKey,
            encryptedPassword = if (authType == SshAuthType.PASSWORD) {
                password.takeIf { it.isNotEmpty() }?.let(SecretCipher::encrypt) ?: previous?.encryptedPassword
            } else null,
            encryptedPrivateKey = if (authType == SshAuthType.PRIVATE_KEY) {
                privateKey.takeIf { it.isNotBlank() }?.let { SecretCipher.encrypt(it.trim()) }
                    ?: previous?.encryptedPrivateKey
            } else null,
            encryptedPassphrase = when {
                authType != SshAuthType.PRIVATE_KEY -> null
                passphrase.isNotEmpty() -> SecretCipher.encrypt(passphrase)
                keepPrivateKey -> previous?.encryptedPassphrase
                else -> null
            },
        )
    }

    private fun decryptSecret(value: String): String = try {
        SecretCipher.decrypt(value)
    } catch (e: Exception) {
        throw IOException(
            "Saved SSH credentials cannot be decrypted (for example after restoring a backup on another device). " +
                "Re-enter them in the workspace settings.",
            e,
        )
    }

    private suspend fun <T> readFromBackend(id: String, block: (WorkspaceBackend) -> T): T {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        return try {
            lifecycleMutex.withLock {
                runInterruptible(Dispatchers.IO) { block(backendOf(workspace)) }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    private suspend fun <T> withBackend(id: String, block: (WorkspaceBackend) -> T): T =
        lifecycleMutex.withLock {
            val workspace = dao.getById(id) ?: error("Workspace not found: $id")
            withContext(Dispatchers.IO) { block(backendOf(workspace)) }
        }

    private suspend fun restoreShellState(workspace: WorkspaceEntity) {
        updateShellState(workspace.id, workspace.shellStatus)
    }

    private suspend fun updateShellState(workspace: WorkspaceEntity, shellStatus: String) {
        updateShellState(workspace.id, shellStatus)
    }

    private suspend fun updateShellState(workspaceId: String, shellStatus: String) {
        dao.updateShellStatus(
            id = workspaceId,
            shellStatus = shellStatus,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "WorkspaceRepository"
        private const val MAX_PREVIEW_BYTES = 512L * 1024
    }
}
