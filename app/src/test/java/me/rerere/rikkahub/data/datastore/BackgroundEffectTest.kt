package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.ui.components.ai.shouldUseNativeLiquidGlass
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundEffectTest {
    @Test
    fun `native liquid glass is limited to enabled liquid mode on api 33 plus`() {
        val liquid = DisplaySetting(
            enableBlurEffect = true,
            backgroundEffectType = BackgroundEffectType.LIQUID,
        )

        assertTrue(shouldUseNativeLiquidGlass(33, liquid, unavailable = false))
        assertTrue(shouldUseNativeLiquidGlass(37, liquid, unavailable = false))
        assertFalse(shouldUseNativeLiquidGlass(32, liquid, unavailable = false))
        assertFalse(shouldUseNativeLiquidGlass(33, liquid.copy(enableBlurEffect = false), unavailable = false))
        assertTrue(shouldUseNativeLiquidGlass(33, liquid.copy(liquidCompatMode = true), unavailable = false))
        assertFalse(shouldUseNativeLiquidGlass(33, liquid, unavailable = true))
    }

    @Test
    fun `only liquid mode initializes native rendering`() {
        assertFalse(
            shouldUseNativeLiquidGlass(
                35,
                DisplaySetting(enableBlurEffect = true, backgroundEffectType = BackgroundEffectType.BLUR),
                unavailable = false,
            )
        )
        assertFalse(
            shouldUseNativeLiquidGlass(
                35,
                DisplaySetting(enableBlurEffect = true, backgroundEffectType = BackgroundEffectType.GLASS),
                unavailable = false,
            )
        )
    }

    @Test
    fun `legacy glass setting is normalized to liquid mode and clears the deprecated compatibility flag`() {
        val legacy = JsonInstant.decodeFromString<DisplaySetting>(
            """{"backgroundEffectType":"glass","liquidCompatMode":true}"""
        )

        val normalized = legacy.normalizeLegacyBackgroundEffect()

        assertEquals(BackgroundEffectType.LIQUID, normalized.backgroundEffectType)
        assertFalse(normalized.liquidCompatMode)
    }

    @Test
    fun `liquid compatibility setting keeps its persisted value`() {
        val liquid = JsonInstant.decodeFromString<DisplaySetting>(
            """{"backgroundEffectType":"liquid","liquidCompatMode":false}"""
        )
        val compatible = JsonInstant.decodeFromString<DisplaySetting>(
            """{"backgroundEffectType":"liquid","liquidCompatMode":true}"""
        )

        assertFalse(liquid.normalizeLegacyBackgroundEffect().liquidCompatMode)
        assertTrue(compatible.normalizeLegacyBackgroundEffect().liquidCompatMode)
    }
}
