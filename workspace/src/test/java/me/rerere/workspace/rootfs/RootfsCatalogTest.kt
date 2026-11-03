package me.rerere.workspace.rootfs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsCatalogTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `catalog selects base dev min and keeps unknown distributions`() = runBlocking {
        val packages = listOf("ubuntu-26.04" to "dev", "ubuntu-26.04" to "min", "debian-13" to "base", "void" to "min").joinToString(",") { (id, tier) ->
            """{"id":"$id","arch":"arm64","tier":"$tier","size":1,"sha256":"${"a".repeat(64)}","url":"https://example.org/$id.tar.gz"}"""
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val client = RootfsIndexClient(File(temp.root, "index.json"), { """{"packages":[$packages]}""" })
            val catalog = RootfsCatalog(client, scope)
            catalog.refresh()
            val ready = withTimeout(5_000) { catalog.state.first { it is CatalogState.Ready } } as CatalogState.Ready
            assertEquals("dev", ready.builtin.first { it.id.value == "ubuntu-26.04" }.tier.token)
            assertEquals("base", ready.builtin.first { it.id.value == "debian-13" }.tier.token)
            assertEquals(1, catalog.byId(RootfsId("void")).size)
        } finally { scope.cancel() }
    }

    @Test fun `natural ordering handles numeric release segments`() {
        assertTrue(compareRootfsVersions("3.24", "3.9") > 0)
        assertTrue(compareRootfsVersions("13", "12") > 0)
        assertEquals(0, compareRootfsVersions("rolling", "rolling"))
    }
}
