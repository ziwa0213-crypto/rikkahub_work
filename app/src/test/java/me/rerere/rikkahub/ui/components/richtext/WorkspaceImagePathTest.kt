package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.*
import org.junit.Test

class WorkspaceImagePathTest {
    @Test fun `preview resolves relative images against file directory including root`() {
        assertEquals("docs/img/a.png", relativeWorkspaceImagePath("./img/a.png", "docs"))
        assertEquals("img/a.png", relativeWorkspaceImagePath("./img/a.png", ""))
        assertEquals("docs/../img/a.png", relativeWorkspaceImagePath("../img/a.png", "docs"))
        assertEquals("docs/a%20b.png", relativeWorkspaceImagePath("a%20b.png", "docs"))
    }

    @Test fun `chat relative paths and existing absolute or remote URLs are untouched`() {
        assertNull(relativeWorkspaceImagePath("img/a.png", null))
        listOf("/workspace/a.png", "//example.com/a.png", "https://example.com/a.png", "http://example.com/a.png",
            "data:image/png;base64,x", "HTTPS://example.com/a.png").forEach { assertNull(it, relativeWorkspaceImagePath(it, "docs")) }
    }
}
