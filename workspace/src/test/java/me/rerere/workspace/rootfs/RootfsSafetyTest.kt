package me.rerere.workspace.rootfs

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class RootfsSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private val arch = RootfsArch.ARM64
    private fun installer(manager: WorkspaceManager) = RootfsInstaller(manager, deviceArch = { arch })
    private fun manager() = WorkspaceManager(temp.newFolder()).also {
        it.ensureWorkspace("test")
        File(it.linuxDir("test"), "old.txt").writeText("preserve me")
        File(it.filesDir("test"), "user.txt").writeText("user data")
    }

    @Test fun `failed configuration rolls back the exact old tree and preserves workspace files`() {
        val manager = manager()
        serve(archive()) { url ->
            val spec = object : RootfsSpec by GenericPosixSpec(arch, url) {
                override fun configure(rootfsDir: File) { error("configuration failed") }
            }
            assertThrows(IllegalStateException::class.java) { installer(manager).install("test", spec) }
        }
        assertOld(manager)
        assertFalse(File(manager.tempDir("test"), "linux.old").exists())
    }

    @Test fun `cancellation after replacement restores old environment`() {
        val manager = manager()
        serve(archive()) { url ->
            assertThrows(CancellationException::class.java) {
                installer(manager).install("test", url) {
                    if (it.stage == RootfsInstallStage.CONFIGURING) throw CancellationException("cancel")
                }
            }
        }
        assertOld(manager)
    }

    @Test fun `checksum failures try every source then leave old tree intact`() {
        val manager = manager()
        var requests = 0
        serve(archive(), onRequest = { requests++ }) { url ->
            val spec = object : RootfsSpec by GenericPosixSpec(arch, url, "0".repeat(64)) {
                override fun urls() = listOf(url, url, url)
            }
            val error = assertThrows(RootfsInstallException::class.java) { installer(manager).install("test", spec) }
            assertEquals(RootfsError.CHECKSUM, error.code)
            assertEquals(listOf("3"), error.arguments)
        }
        assertEquals(3, requests)
        assertOld(manager)
        assertTrue(manager.tempDir("test").listFiles().orEmpty().isEmpty())
    }

    @Test fun `mismatched hash falls back to valid next source`() {
        val good = archive()
        val file = temp.newFile().apply { writeBytes(good) }
        val hash = RootfsVerifier.sha256(file)
        val manager = manager()
        serve("corrupt".toByteArray()) { badUrl -> serve(good) { goodUrl ->
            val spec = object : RootfsSpec by GenericPosixSpec(arch, goodUrl, hash) {
                override fun urls() = listOf(badUrl, goodUrl)
            }
            installer(manager).install("test", spec)
        } }
        assertTrue(manager.hasRootfs("test"))
        assertFalse(File(manager.linuxDir("test"), "old.txt").exists())
        assertEquals("user data", File(manager.filesDir("test"), "user.txt").readText())
    }

    @Test fun `local wrong architecture and desktop are rejected before swapping`() {
        for ((bytes, reason) in listOf(archive(machine = 62) to RootfsError.ARCH_MISMATCH, archive(desktop = true) to RootfsError.DESKTOP)) {
            val manager = manager()
            val failure = assertThrows(RootfsInstallException::class.java) {
                installer(manager).installLocal("test", "local.tar.gz", bytes.size.toLong(), { bytes.inputStream() })
            }
            assertEquals(reason, failure.code)
            assertOld(manager)
        }
    }

    @Test fun `unsupported extension is rejected before opening local source`() {
        val manager = manager()
        assertThrows(RootfsInstallException::class.java) {
            installer(manager).installLocal("test", "local.zip", 123L, { error("must not open") })
        }
        assertOld(manager)
    }

    @Test fun `absolute guest symlinks resolve inside rootfs and cleanup does not traverse host links`() {
        val outside = temp.newFolder()
        val marker = File(outside, "keep").apply { writeText("keep") }
        val manager = manager()
        val bytes = archive(extraLink = outside.absolutePath)
        serve(bytes) { url -> installer(manager).install("test", url) }
        assertTrue(manager.hasRootfs("test"))
        serve(bytes) { url -> installer(manager).install("test", url) }
        assertEquals("keep", marker.readText())
        val root = manager.linuxDir("test")
        assertEquals(File(root, "usr/bin/sh"), rootfsPath(root, "bin/sh"))
        assertEquals("unknown", RootfsStamp.readFrom(root)?.distroId)
    }

    @Test fun `file cap rejects oversized archive while preserving old environment`() {
        val manager = manager()
        val bytes = ByteArrayOutputStream().also { buf -> GZIPOutputStream(buf).use { gzip ->
            repeat(RootfsVerifier.MAX_ENTRIES + 1) { gzip.entry("f$it", ByteArray(0)) }
            gzip.write(ByteArray(1024))
        } }.toByteArray()
        val error = assertThrows(RootfsInstallException::class.java) {
            installer(manager).installLocal("test", "local.tgz", bytes.size.toLong(), { bytes.inputStream() })
        }
        assertEquals(RootfsError.TOO_MANY_FILES, error.code)
        assertOld(manager)
    }

    @Test fun `traversal in tar cannot write outside staging`() {
        val bytes = ByteArrayOutputStream().also { buf -> GZIPOutputStream(buf).use { out -> out.entry("../escaped", "bad".toByteArray()) } }.toByteArray()
        val dir = temp.newFolder()
        val archive = temp.newFile("bad.tgz").apply { writeBytes(bytes) }
        assertThrows(IllegalArgumentException::class.java) { installer(manager()).extractTar(archive, dir) {} }
        assertFalse(File(dir.parentFile, "escaped").exists())
    }

    private fun assertOld(manager: WorkspaceManager) {
        assertEquals("preserve me", File(manager.linuxDir("test"), "old.txt").readText())
        assertEquals("user data", File(manager.filesDir("test"), "user.txt").readText())
    }

    @Test fun `failed staging rename restores old environment even before configure`() {
        val manager = manager()
        val installer = RootfsInstaller(manager, deviceArch = { arch }, moveDirectory = { from, to ->
            if (from.name == "rootfs-staging") false else from.renameTo(to)
        })
        serve(archive()) { url -> assertThrows(IllegalStateException::class.java) { installer.install("test", url) } }
        assertOld(manager)
    }

    private fun archive(machine: Int = 183, desktop: Boolean = false, extraLink: String? = null): ByteArray =
        ByteArrayOutputStream().also { buffer -> GZIPOutputStream(buffer).use { out ->
            val elf = ByteArray(64).apply {
                byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 2, 1).copyInto(this)
                this[18] = machine.toByte()
            }
            out.entry("usr/bin/sh", elf)
            out.entry("bin", ByteArray(0), '2', "/usr/bin")
            out.entry("etc/android-proot-release", "distro=unknown\narch=arm64\ntier=base\n".toByteArray())
            if (desktop) out.entry("usr/bin/Xorg", ByteArray(0))
            if (extraLink != null) out.entry("host-link", ByteArray(0), '2', extraLink)
            out.write(ByteArray(1024))
        } }.toByteArray()

    private fun serve(bytes: ByteArray, onRequest: () -> Unit = {}, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rootfs.tar.gz") {
            onRequest()
            it.sendResponseHeaders(200, bytes.size.toLong())
            it.responseBody.use { output -> output.write(bytes) }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/rootfs.tar.gz") } finally { server.stop(0) }
    }

    private fun OutputStream.entry(name: String, data: ByteArray, type: Char = '0', target: String = "") {
        val header = ByteArray(512)
        name.toByteArray().copyInto(header)
        "0000755".toByteArray().copyInto(header, 100)
        data.size.toString(8).padStart(11, '0').toByteArray().copyInto(header, 124)
        header[156] = type.code.toByte()
        target.toByteArray().copyInto(header, 157)
        write(header)
        write(data)
        write(ByteArray((512 - data.size % 512) % 512))
    }
}
