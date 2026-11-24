package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionSelectorTest {
    @Test
    fun `chat keeps all extension tabs`() {
        assertEquals(ExtensionTab.entries, extensionTabs(includeQuickMessages = true, allowedTabs = null))
    }

    @Test
    fun `group without an explicit allowlist hides quick messages and lorebooks`() {
        assertEquals(
            listOf(ExtensionTab.MODE_INJECTIONS, ExtensionTab.SKILLS),
            extensionTabs(includeQuickMessages = false, allowedTabs = null, groupMode = true),
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
    fun `group tabs never expose lorebooks regardless of experimental mode`() {
        assertEquals(
            listOf(ExtensionTab.MODE_INJECTIONS, ExtensionTab.SKILLS),
            extensionTabs(includeQuickMessages = false, allowedTabs = GroupExtensionTabs),
        )
        assertFalse(ExtensionTab.LOREBOOKS in GroupExtensionTabs)
    }

    @Test
    fun `group mode cannot be overridden to expose lorebooks`() {
        assertEquals(
            listOf(ExtensionTab.SKILLS),
            extensionTabs(
                includeQuickMessages = true,
                allowedTabs = setOf(ExtensionTab.LOREBOOKS, ExtensionTab.SKILLS),
                groupMode = true,
            ),
        )
    }

    @Test
    fun `work and character lorebook tabs follow the character experiment gate`() {
        assertTrue(ExtensionTab.LOREBOOKS in WorkExtensionTabs)
        assertFalse(ExtensionTab.LOREBOOKS in ExperimentalWorkExtensionTabs)
        assertTrue(ExtensionTab.LOREBOOKS in CharacterExtensionTabs)
        assertFalse(ExtensionTab.LOREBOOKS in DisabledCharacterExtensionTabs)
    }

    @Test
    fun `explicit tabs take precedence over the legacy quick message filter`() {
        assertEquals(
            listOf(ExtensionTab.QUICK_MESSAGES),
            extensionTabs(includeQuickMessages = false, allowedTabs = setOf(ExtensionTab.QUICK_MESSAGES)),
        )
    }
}
