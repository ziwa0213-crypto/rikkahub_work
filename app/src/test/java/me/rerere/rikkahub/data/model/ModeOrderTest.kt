package me.rerere.rikkahub.data.model

import org.junit.Assert.*
import org.junit.Test
import me.rerere.rikkahub.data.datastore.*

class ModeOrderTest {
    @Test fun launchPreferenceIsIndependentOfLastMode() {
        val settings = Settings(defaultMode = UiMode.ROLE, uiMode = UiMode.PULSE)
        assertEquals(UiMode.ROLE, settings.launchMode())
        assertEquals(listOf(UiMode.ROLE, UiMode.CHAT, UiMode.GROUP, UiMode.PULSE), settings.orderedModes())
        assertEquals(UiMode.PULSE, settings.uiMode)
        assertEquals(UiMode.CHAT, settings.copy(defaultMode = UiMode.GROUP).launchMode())
        assertEquals(UiMode.ROLE, settings.copy(uiMode = UiMode.ROLE, experimentalCharacterMode = false).normalized().uiMode)
    }
}
