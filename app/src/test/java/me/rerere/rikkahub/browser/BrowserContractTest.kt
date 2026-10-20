package me.rerere.rikkahub.browser

import kotlinx.serialization.json.buildJsonObject
import me.rerere.rikkahub.data.ai.tools.tierOf
import me.rerere.rikkahub.data.ai.tools.ToolTier
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class BrowserContractTest {
    @Test fun rejectsNonWebAndCredentialUrls() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "content://private", "https:", "https:///path", "https://user:pass@example.org", "https://").forEach {
            assertNotNull(it, BrowserController.validateUrl(it))
        }
        listOf("https://example.org", "http://localhost:8080/a?x=1", "https://example.org/#hi").forEach {
            assertNull(it, BrowserController.validateUrl(it))
        }
    }
    @Test fun desktopUaUsesRuntimeEngine() {
        val ua = "Mozilla/5.0 (Linux; Android 16; Pixel; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7.2 Mobile Safari/537.36"
        val desktop = BrowserUserAgent.desktop(ua)
        assertTrue(desktop.contains("Chrome/150.0.7.2"))
        assertTrue(desktop.contains("X11; Linux x86_64"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
    }
    @Test fun allRiskTiersAreExplicit() {
        val args = buildJsonObject {}
        listOf("browser_read", "browser_list_actions", "browser_state", "browser_done").forEach {
            assertEquals(ToolTier.L0_READ, tierOf(it, args))
        }
        assertEquals(ToolTier.L1_SELF, tierOf("browser_scroll", args))
        listOf("browser_open", "browser_click", "browser_type").forEach {
            assertEquals(ToolTier.L3_REWRITE, tierOf(it, args))
        }
    }
    @Test fun generatedScriptsKeepParametersAsData() {
        val malicious = "');document.cookie;//\n\""
        assertTrue(BrowserJsScripts.click(malicious).contains("resolve(\""))
        assertTrue(BrowserJsScripts.typeInto("id", malicious).contains("\\n"))
        assertTrue(BrowserJsScripts.listClickables(100, "nonce").contains(">= 60"))
        assertTrue(BrowserJsScripts.cleanDocument.contains("getComputedStyle"))
        assertFalse(BrowserJsScripts.cleanDocument.contains("node.value"))
    }
    @Test fun desktopViewportScriptOnlyChangesViewportAndRootWidth() {
        val script = BrowserDesktopViewport.SCRIPT
        assertTrue(script.contains("meta[name=\"viewport\"]"))
        assertTrue(script.contains("width=' + w + ', initial-scale=1"))
        assertTrue(script.contains("document.documentElement.style.width"))
        assertTrue(script.contains("document.documentElement.style.minWidth"))
        listOf("document.cookie", "fetch(", "XMLHttpRequest", "innerHTML", "textContent").forEach {
            assertFalse("unexpected page access: $it", script.contains(it))
        }
    }
    @Test fun exportActualScriptsForOfflineDomFixture() {
        val dir = File("build/browser-js-contract").apply { mkdirs() }
        File(dir, "scripts.json").writeText(Json.encodeToString(mapOf(
            "list" to BrowserJsScripts.listClickables(60, "fixture"),
            "relist" to BrowserJsScripts.listClickables(60, "updated"),
            "click" to BrowserJsScripts.click("fixture-0"),
            "type" to BrowserJsScripts.typeInto("fixture-0", "safe query"),
            "inspect" to BrowserJsScripts.inspect("fixture-0"),
            "read" to BrowserJsScripts.readText,
            "state" to BrowserJsScripts.titleAndUrl,
            "selection" to BrowserJsScripts.selection,
        )))
        assertTrue(File(dir, "scripts.json").length() > 0)
    }
}
