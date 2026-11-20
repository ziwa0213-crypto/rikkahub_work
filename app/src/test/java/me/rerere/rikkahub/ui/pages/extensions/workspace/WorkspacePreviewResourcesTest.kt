package me.rerere.rikkahub.ui.pages.extensions.workspace

import java.io.File
import java.net.URI
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspacePreviewResourcesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `same directory and nested CSS resources resolve without double prefix`() {
        val directory = temporary.newFolder("workspace", "docs")
        val css = File(directory, "style.css").apply { writeText("body{}") }
        val nested = File(directory, "img").apply { mkdir() }
        val image = File(nested, "a.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val resolver = WorkspacePreviewResources(directory)
        assertEquals(css.canonicalFile, resolver.resolve(URI(WORKSPACE_PREVIEW_BASE_URL).resolve("style.css").toString()))
        assertEquals(image.canonicalFile, resolver.resolve(URI(WORKSPACE_PREVIEW_BASE_URL).resolve("./img/a.png").toString()))
        assertEquals(css.canonicalFile, resolver.resolve("${WORKSPACE_PREVIEW_BASE_URL}style.css?v=2#anchor"))
    }

    @Test fun `offline resolver rejects external origins non HTTPS methods and missing roots`() {
        val root = temporary.newFolder()
        File(root, "a.txt").writeText("hello")
        val resolver = WorkspacePreviewResources(root)
        listOf("https://example.com/a.txt", "http://workspace-preview.invalid/preview/a.txt",
            "file:///etc/passwd", "content://workspace-preview.invalid/preview/a.txt",
            "https://workspace-preview.invalid.evil/preview/a.txt", "https://workspace-preview.invalid:444/preview/a.txt",
            "https://evil@workspace-preview.invalid/preview/a.txt", "bad URI").forEach { assertNull(it, resolver.resolve(it)) }
        assertNull(resolver.resolve("${WORKSPACE_PREVIEW_BASE_URL}a.txt", "POST"))
        assertNotNull(resolver.resolve("${WORKSPACE_PREVIEW_BASE_URL}a.txt", "HEAD"))
        assertNull(WorkspacePreviewResources(null).resolve("${WORKSPACE_PREVIEW_BASE_URL}a.txt"))
    }

    @Test fun `traversal encoded paths and sibling prefix cannot escape document directory`() {
        val base = temporary.newFolder("base")
        val root = File(base, "docs").apply { mkdir() }
        File(base, "secret.txt").writeText("secret")
        val resolver = WorkspacePreviewResources(root)
        listOf("../secret.txt", "%2e%2e/secret.txt", "%2e%2e%2fsecret.txt", "../../etc/passwd",
            "%00secret.txt", "..%5csecret.txt", "/etc/passwd").forEach {
            assertNull(it, resolver.resolve(WORKSPACE_PREVIEW_BASE_URL + it))
        }
        assertNull(resolver.resolve("https://workspace-preview.invalid/secret.txt"))
        assertNull(resolver.resolve(WORKSPACE_PREVIEW_BASE_URL))
    }

    @Test fun `symlinks may point inside document directory but cannot expose siblings`() {
        val root = temporary.newFolder("docs")
        val allowed = File(root, "image.png").apply { writeText("ok") }
        val secret = temporary.newFile("secret.txt").apply { writeText("secret") }
        Files.createSymbolicLink(File(root, "inside.png").toPath(), allowed.toPath())
        Files.createSymbolicLink(File(root, "escape.txt").toPath(), secret.toPath())
        val resolver = WorkspacePreviewResources(root)
        assertEquals(allowed.canonicalFile, resolver.resolve("${WORKSPACE_PREVIEW_BASE_URL}inside.png"))
        assertNull(resolver.resolve("${WORKSPACE_PREVIEW_BASE_URL}escape.txt"))
    }

    @Test fun `unicode and spaces decode once and two roots remain isolated`() {
        val first = temporary.newFolder("one")
        val second = temporary.newFolder("two")
        val a = File(first, "图 a.png").apply { writeText("first") }
        val b = File(second, "图 a.png").apply { writeText("second") }
        val url = WORKSPACE_PREVIEW_BASE_URL + "%E5%9B%BE%20a.png"
        assertEquals(a.canonicalFile, WorkspacePreviewResources(first).resolve(url))
        assertEquals(b.canonicalFile, WorkspacePreviewResources(second).resolve(url))
    }
}
