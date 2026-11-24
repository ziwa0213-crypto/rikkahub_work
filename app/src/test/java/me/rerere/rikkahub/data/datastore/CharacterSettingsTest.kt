package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test

class CharacterSettingsTest {
    @Test fun `legacy chat maps to work in both storage formats`() {
        for ((raw, mode) in mapOf("CHAT" to UiMode.CHAT, "ROLE" to UiMode.ROLE,
            "GROUP" to UiMode.GROUP, "PULSE" to UiMode.PULSE)) {
            val disk = mutablePreferencesOf(SettingsStore.UI_MODE to raw)
            assertEquals(mode, disk.toSettings().uiMode)
            assertEquals(mode, JsonInstant.decodeFromString<Settings>("""{"uiMode":"$raw"}""").uiMode)
        }
        assertEquals(UiMode.CHAT, mutablePreferencesOf(SettingsStore.UI_MODE to "UNKNOWN").toSettings().uiMode)
        assertEquals(UiMode.CHAT, JsonInstant.decodeFromString<Settings>("{}").uiMode)
        val work = Settings(uiMode = UiMode.CHAT, characters = listOf(Character(name = "A")))
        val disk = mutablePreferencesOf().apply { putSettings(work) }
        assertEquals("CHAT", disk[SettingsStore.UI_MODE])
        assertEquals(work.characters, disk.toSettings().characters)
        // Explicitly encode the enum, since Settings may omit its default value.
        assertEquals("\"CHAT\"", JsonInstant.encodeToString(UiMode.CHAT))
        val backup = JsonInstant.encodeToString(work)
        assertNotNull(JsonInstant.parseToJsonElement(backup).jsonObject["characters"])
        assertEquals(work.characters, JsonInstant.decodeFromString<Settings>(backup).characters)
    }

    @Test fun `new installs and restored backups do not prompt`() {
        val old = JsonInstant.decodeFromString<Settings>("""{"uiMode":"CHAT"}""").copy(init = false)
        assertFalse(old.shouldOfferCharacterMigration(1))
        assertTrue(old.shouldOfferCharacterMigration(2))
        assertFalse(old.copy(init = true).shouldOfferCharacterMigration(8))
        assertFalse(old.copy(characterMigrationState = CharacterMigrationState.DECLINED).shouldOfferCharacterMigration(8))
        val restored = old.withRestoredCharacterMigration(209)
        assertEquals(209, restored.lastRunVersionCode)
        assertEquals(UiMode.CHAT, restored.uiMode)
        assertEquals(old.assistants, restored.assistants)
        assertFalse(restored.shouldOfferCharacterMigration(8))
    }

    @Test fun `character migration is offered only after manual opt in`() {
        val fresh = JsonInstant.decodeFromString<Settings>("""{"uiMode":"CHAT"}""").copy(init = false)
        val markedFresh = fresh.markNewInstallVersionIfNeeded(launches = 1, versionCode = 210)
        assertFalse(markedFresh.shouldOfferCharacterMigrationManually(2))
        assertEquals(210, markedFresh.lastRunVersionCode)
        assertEquals(fresh, fresh.markNewInstallVersionIfNeeded(launches = 2, versionCode = 210))
        assertFalse(fresh.copy(characterMigrationPrompted = true).shouldOfferCharacterMigrationManually(2))
        assertFalse(fresh.copy(characterMigrationState = CharacterMigrationState.DECLINED).shouldOfferCharacterMigrationManually(2))
        assertFalse(fresh.copy(lastRunVersionCode = 210).shouldOfferCharacterMigrationManually(2))
    }

    @Test fun `legacy users keep the zero marker until manual migration check`() {
        val legacy = JsonInstant.decodeFromString<Settings>("""{"uiMode":"CHAT"}""").copy(init = false)
        assertEquals(legacy, legacy.markNewInstallVersionIfNeeded(launches = 2, versionCode = 210))
        assertTrue(legacy.shouldOfferCharacterMigrationManually(2))
    }
}
