package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionSelectorTest {
    @Test
    fun `chat keeps all extension tabs`() {
        assertEquals(ExtensionTab.entries, extensionTabs(includeQuickMessages = true, allowedTabs = null))
    }

    @Test
    fun `group keeps all tabs except quick messages`() {
        assertEquals(
            listOf(ExtensionTab.MODE_INJECTIONS, ExtensionTab.LOREBOOKS, ExtensionTab.SKILLS),
            extensionTabs(includeQuickMessages = false, allowedTabs = null),
        )
    }

    @Test
    fun `pulse keeps quick messages and skills only`() {
        assertEquals(
            listOf(ExtensionTab.QUICK_MESSAGES, ExtensionTab.SKILLS),
            extensionTabs(includeQuickMessages = true, allowedTabs = PulseExtensionTabs),
        )
    }

    @Test
    fun `explicit tabs take precedence over the legacy quick message filter`() {
        assertEquals(
            listOf(ExtensionTab.QUICK_MESSAGES),
            extensionTabs(includeQuickMessages = false, allowedTabs = setOf(ExtensionTab.QUICK_MESSAGES)),
        )
    }
}
