package me.rerere.ui.webview

import org.junit.Assert.*
import org.junit.Test

class MimeTypesTest {
    @Test fun `local browser resources preserve MIME and encoding`() {
        val cases = mapOf("main.js" to "text/javascript", "module.mjs" to "text/javascript", "main.CSS" to "text/css",
            "index.html" to "text/html", "data.json" to "application/json", "image.svg" to "image/svg+xml",
            "a.png" to "image/png", "a.jpg" to "image/jpeg", "a.webp" to "image/webp", "a.gif" to "image/gif",
            "a.ico" to "image/x-icon", "font.woff2" to "font/woff2", "a.md" to "text/plain", "data.csv" to "text/plain")
        cases.forEach { (name, mime) -> assertEquals(name, mime, mimeTypeOf(name)) }
        assertEquals("application/octet-stream", mimeTypeOf("unknown.bin"))
        assertTrue(isTextualMime("application/json"))
        assertTrue(isTextualMime("image/svg+xml"))
        assertFalse(isTextualMime("image/png"))
        assertFalse(isTextualMime("font/woff2"))
    }
}
