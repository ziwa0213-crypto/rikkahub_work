package me.rerere.rikkahub.data.repository

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.WorkspaceOwnerScope
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.uuid.Uuid

class WorkspaceBasicTest {
    @get:Rule val temp = TemporaryFolder()

    private class Dao : WorkspaceDAO {
        val rows = MutableStateFlow<List<WorkspaceEntity>>(emptyList())
        var writes = 0
        override fun listFlow(): Flow<List<WorkspaceEntity>> = rows
        override suspend fun getById(id: String) = rows.value.find { it.id == id }
        override suspend fun getAll() = rows.value
        override suspend fun upsert(workspace: WorkspaceEntity) {
            writes++
            rows.value = rows.value.filterNot { it.id == workspace.id } + workspace
        }
        override suspend fun updateShellStatus(id: String, shellStatus: String, updatedAt: Long): Int {
            val row = getById(id) ?: return 0
            upsert(row.copy(shellStatus = shellStatus, updatedAt = updatedAt))
            return 1
        }
        override suspend fun setShellCompatibilityMode(id: String, enabled: Boolean, updatedAt: Long): Int {
            val row = getById(id) ?: return 0
            upsert(row.copy(shellCompatibilityMode = enabled, updatedAt = updatedAt))
            return 1
        }
        override suspend fun deleteById(id: String): Int {
            val count = rows.value.count { it.id == id }
            rows.value = rows.value.filterNot { it.id == id }
            return count
        }
    }

    private inner class Fixture {
        val manager = WorkspaceManager(baseDir = temp.newFolder())
        val dao = Dao()
        var settings = Settings()
        val repository = WorkspaceRepository(dao, manager, RootfsInstaller(manager)) { settings = it(settings) }

        suspend fun ready(): WorkspaceEntity {
            val workspace = repository.create("Source", "chat")
            val ready = workspace.copy(shellStatus = WorkspaceShellStatus.READY.name)
            File(manager.linuxDir(workspace.root), "bin").mkdirs()
            File(manager.linuxDir(workspace.root), "bin/sh").writeText("shell fixture")
            dao.upsert(ready)
            return ready
        }
    }

    @Test fun `all ownership scopes create independent disabled workspaces`() = runBlocking {
        val f = Fixture()
        val roots = mutableSetOf<File>()
        for (owner in listOf(null, "chat", "group")) {
            val workspace = f.repository.create("Workspace-${owner ?: "none"}", owner)
            assertEquals(owner, workspace.owner)
            assertNull(workspace.sharedRootfsFrom)
            assertEquals(WorkspaceShellStatus.DISABLED.name, workspace.shellStatus)
            assertTrue(f.manager.filesDir(workspace.root).isDirectory)
            assertTrue(f.manager.linuxDir(workspace.root).isDirectory)
            assertTrue(roots.add(f.manager.linuxDir(workspace.root)))
        }
        assertTrue(runCatching { f.repository.create("Invalid", "invalid") }.isFailure)
        assertEquals(3, f.dao.getAll().size)
    }

    @Test fun `ownership changes affect selection without unbinding or modifying files`() = runBlocking {
        val f = Fixture()
        val workspace = f.repository.create("Bound", "group")
        f.settings = Settings(assistants = listOf(Assistant(workspaceId = Uuid.parse(workspace.id))),
            groups = listOf(Group(workspaceId = Uuid.parse(workspace.id))))
        val settingsBefore = f.settings
        f.repository.writeText(workspace.id, "preserve.txt", "private", true)
        for (owner in listOf("chat", "group", null)) {
            assertTrue(f.repository.setOwner(workspace.id, owner))
            val after = f.repository.getById(workspace.id)!!
            assertEquals(workspace.copy(owner = owner, updatedAt = after.updatedAt), after)
            assertEquals(settingsBefore, f.settings)
            assertEquals(owner == null || owner == "chat", WorkspaceOwnerScope.CHAT.includes(after))
            assertEquals(owner == null || owner == "group", WorkspaceOwnerScope.GROUP.includes(after))
            assertEquals("private", f.repository.readText(workspace.id, "preserve.txt"))
        }
        val after = f.repository.getById(workspace.id)
        assertTrue(runCatching { f.repository.setOwner(workspace.id, "invalid") }.isFailure)
        assertEquals(after, f.repository.getById(workspace.id))
        assertFalse(f.repository.setOwner("missing", "chat"))
    }

    @Test fun `integrity retires old shared records once preserving ownership bindings files and source`() = runBlocking {
        val f = Fixture()
        val source = f.ready()
        val consumers = listOf(null, "chat", "group").mapIndexed { index, owner ->
            val consumer = f.repository.create("Old-$index", owner)
            f.repository.writeText(consumer.id, "preserved.txt", "private-$index", true)
            // Historical consumers did not own a Linux directory.
            f.manager.linuxDir(consumer.root).delete()
            consumer.copy(sharedRootfsFrom = if (index == 0) "missing" else source.id,
                shellStatus = if (index == 0) "BROKEN" else "READY", updatedAt = 0).also { f.dao.upsert(it) }
        }
        f.settings = Settings(assistants = listOf(Assistant(workspaceId = Uuid.parse(consumers.first().id))),
            groups = listOf(Group(workspaceId = Uuid.parse(consumers.last().id))))
        val settingsBefore = f.settings
        f.repository.checkIntegrity()
        consumers.forEachIndexed { index, before ->
            val after = f.repository.getById(before.id)!!
            assertNull(after.sharedRootfsFrom)
            assertEquals("DISABLED", after.shellStatus)
            assertEquals(before.copy(sharedRootfsFrom = null, shellStatus = "DISABLED", updatedAt = after.updatedAt), after)
            assertTrue(after.updatedAt > 0)
            assertFalse(f.manager.linuxDir(after.root).exists())
            assertEquals("private-$index", File(f.manager.filesDir(after.root), "preserved.txt").readText())
            assertNotEquals(f.manager.linuxDir(source.root), f.manager.linuxDir(after.root))
        }
        assertEquals(source, f.repository.getById(source.id))
        assertTrue(f.manager.hasRootfs(source.root))
        assertEquals(settingsBefore, f.settings)
        val records = f.dao.getAll().toSet()
        val writes = f.dao.writes
        f.repository.checkIntegrity()
        assertEquals(records, f.dao.getAll().toSet())
        assertEquals(writes, f.dao.writes)
    }

    @Test fun `retired workspace reaches independent installer and cancellation preserves former source`() = runBlocking {
        val f = Fixture()
        val source = f.ready()
        val consumer = f.repository.create("Old group", "group")
        f.dao.upsert(consumer.copy(sharedRootfsFrom = source.id, shellStatus = "READY"))
        f.repository.checkIntegrity()
        // Loopback-only fixture reaches the real download callback, then cancels
        // before extraction. This also checks that cancellation restores DISABLED.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rootfs.tar.gz") { exchange ->
            val bytes = "local fixture".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        var downloaded = false
        try {
            val failure = runCatching {
                f.repository.installRootfs(consumer.id, "http://127.0.0.1:${server.address.port}/rootfs.tar.gz") {
                    if (it.stage == RootfsInstallStage.DOWNLOADING) {
                        downloaded = true
                        throw CancellationException("fixture cancelled")
                    }
                }
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals("fixture cancelled", failure!!.message)
            assertTrue(downloaded)
        } finally {
            server.stop(0)
        }
        assertEquals("DISABLED", f.repository.getById(consumer.id)!!.shellStatus)
        assertFalse(f.manager.hasRootfs(consumer.root))
        assertEquals(source, f.repository.getById(source.id))
        assertTrue(f.manager.hasRootfs(source.root))
    }

    @Test fun `deletion cleans assistant and group bindings but preserves other workspaces`() = runBlocking {
        val f = Fixture()
        val selected = f.repository.create("Selected", "group")
        val other = f.repository.create("Other", "chat")
        f.settings = Settings(assistants = listOf(Assistant(workspaceId = Uuid.parse(selected.id)),
            Assistant(workspaceId = Uuid.parse(other.id))),
            groups = listOf(Group(workspaceId = Uuid.parse(selected.id)), Group(workspaceId = Uuid.parse(other.id))))
        f.repository.writeText(other.id, "keep.txt", "keep", true)
        assertTrue(f.repository.delete(selected.id))
        assertNull(f.repository.getById(selected.id))
        assertFalse(f.manager.workspaceDir(selected.root).exists())
        assertNull(f.settings.assistants.first().workspaceId)
        assertNull(f.settings.groups.first().workspaceId)
        assertEquals(Uuid.parse(other.id), f.settings.assistants.last().workspaceId)
        assertEquals(Uuid.parse(other.id), f.settings.groups.last().workspaceId)
        assertEquals("keep", f.repository.readText(other.id, "keep.txt"))
        assertFalse(f.repository.delete("missing"))
    }

    @Test fun `concurrent duplicate creation still rejects one attempt`() = runBlocking {
        val f = Fixture()
        val results = coroutineScope {
            listOf(async { runCatching { f.repository.create("Duplicate") } },
                async { runCatching { f.repository.create("Duplicate") } }).map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, f.dao.getAll().count { it.name == "Duplicate" })
    }
}
