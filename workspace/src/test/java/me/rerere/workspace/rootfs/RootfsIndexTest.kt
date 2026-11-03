package me.rerere.workspace.rootfs

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsIndexTest {
    @get:Rule val temp = TemporaryFolder()
    private val pkg = """{"distro":"debian","version":"13","arch":"arm64","tier":"base","size":123,"sha256":"${"a".repeat(64)}","url":"https://example.org/rootfs.tar.gz"}"""
    private fun index(packages: String = pkg, extra: String = "") = """{"schemaVersion":2,"packages":[$packages]$extra}"""

    @Test fun `normal index maps all fields with new schema and unknown fields`() {
        val parsed = RootfsIndex.parse(index(extra = ",\"future\":true"))
        assertEquals(2, parsed.schemaVersion)
        val entry = parsed.entries().single()
        assertEquals("debian-13|arm64|base", entry.key)
        assertEquals(123L, entry.sizeBytes)
        assertEquals("a".repeat(64), entry.sha256)
        assertEquals(EntrySource.INDEX, entry.source)
    }

    @Test fun `malformed items missing url missing sha and unknown tokens are discarded individually`() {
        val entries = RootfsIndex.parse(index(listOf(
            pkg, pkg.replace("\"sha256\"", "\"missing\""), pkg.replace("\"url\"", "\"missing\""),
            pkg.replace("\"arm64\"", "\"armhf\""), pkg.replace("\"base\"", "\"oops\""),
            "{\"size\":{}}", "null",
        ).joinToString(","))).entries()
        assertEquals(1, entries.size)
    }

    @Test fun `legacy arch aliases normalize explicitly and rolling ids have no trailing hyphen`() {
        val entry = RootfsIndex.parse(index(pkg.replace("debian", "archlinux").replace("\"13\"", "\"\"").replace("arm64", "aarch64"))).entries().single()
        assertEquals("archlinux", entry.id.value)
        assertEquals(RootfsArch.ARM64, entry.arch)
        assertEquals("explicit", RootfsPackageJson(id = "explicit", distro = "debian", version = "13").toRootfsId().value)
    }

    @Test fun `invalid json fails safely and raw parse failure tries cdn`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = RootfsIndexClient(File(temp.root, "index.json"), { url ->
            calls += url
            if (calls.size == 1) "{" else index()
        })
        assertTrue(client.load(true).isSuccess)
        assertEquals(RootfsIndexClient.INDEX_URLS, calls)
    }

    @Test fun `network failure reuses persisted cache across clients with original timestamp`() = runBlocking {
        val file = File(temp.root, "index.json")
        val first = RootfsIndexClient(file, { index() }, now = { 10_000L })
        assertFalse(first.load(true).getOrThrow().fromCache)
        val second = RootfsIndexClient(file, { error("offline") }, now = { 20_000L })
        val loaded = second.load(true).getOrThrow()
        assertTrue(loaded.fromCache)
        assertEquals(10_000L, loaded.fetchedAt)
        assertEquals(1, loaded.index.entries().size)
    }

    @Test fun `fresh cache skips foreground request while forced refresh tries both sources`() = runBlocking {
        var calls = 0
        val file = File(temp.root, "index.json")
        file.writeText(index())
        file.setLastModified(10_000L)
        val client = RootfsIndexClient(file, { calls++; error("offline") }, now = { 11_000L })
        assertTrue(client.load(false).getOrThrow().fromCache)
        assertEquals(0, calls)
        assertTrue(client.load(true).getOrThrow().fromCache)
        assertEquals(2, calls)
    }

    @Test fun `corrupt cache behaves as absent and offline returns failure`() = runBlocking {
        val file = temp.newFile().apply { writeText("broken") }
        val client = RootfsIndexClient(file, { error("offline") })
        assertNull(client.readCache())
        assertTrue(client.load(true).isFailure)
    }
}
