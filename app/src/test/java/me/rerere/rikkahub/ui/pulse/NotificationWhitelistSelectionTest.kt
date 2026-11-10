package me.rerere.rikkahub.ui.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.ui.pages.pulse.invertNotificationApps
import me.rerere.rikkahub.ui.pages.pulse.selectAllNotificationApps
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWhitelistSelectionTest {
    private val apps = listOf("app.a", "app.b", "app.c", "app.d", "app.e")

    @Test
    fun `select all retains outside packages and current selection order`() {
        val current = listOf("uninstalled.app", "app.c", "app.a")
        val selected = selectAllNotificationApps(current, apps)
        assertEquals(listOf("uninstalled.app", "app.c", "app.a", "app.b", "app.d", "app.e"), selected)
        assertEquals(apps.size, selected.count { it in apps })
        assertEquals(selected, selectAllNotificationApps(selected, apps))
    }

    @Test
    fun `invert three of five keeps outside packages and yields two of five`() {
        val current = listOf("app.a", "app.c", "app.e", "uninstalled.app")
        val inverted = invertNotificationApps(current, apps)
        assertEquals(listOf("uninstalled.app", "app.b", "app.d"), inverted)
        assertEquals(2, inverted.count { it in apps })
        assertEquals(current.toSet(), invertNotificationApps(inverted, apps).toSet())
    }

    @Test
    fun `empty and duplicate lists do not remove invisible selections`() {
        assertEquals(listOf("outside"), selectAllNotificationApps(listOf("outside"), emptyList()))
        assertEquals(listOf("outside"), invertNotificationApps(listOf("outside"), emptyList()))
        assertEquals(listOf("outside", "a"), selectAllNotificationApps(listOf("outside"), listOf("a", "a")))
        assertEquals(listOf("outside", "a"), invertNotificationApps(listOf("outside"), listOf("a", "a")))
    }

    @Test
    fun `deferred updates read the latest configuration and only change the whitelist`() {
        val operations = listOf<(PulseConfig) -> PulseConfig>(
            { pulse -> pulse.copy(notificationWhitelist = selectAllNotificationApps(pulse.notificationWhitelist, apps)) },
            { pulse -> pulse.copy(notificationWhitelist = invertNotificationApps(pulse.notificationWhitelist, apps)) },
        )
        val latest = PulseConfig(notificationWhitelist = listOf("new.outside", "app.b"),
            enableNotificationReading = true, notificationRetentionHours = 48,
            enableSmsReading = true, systemPrompt = "Keep me", heartbeatIntervalMinutes = 47)
        val selected = operations[0](latest)
        assertEquals(listOf("new.outside", "app.b", "app.a", "app.c", "app.d", "app.e"), selected.notificationWhitelist)
        assertEquals(latest, selected.copy(notificationWhitelist = latest.notificationWhitelist))
        val inverted = operations[1](selected)
        assertEquals(listOf("new.outside"), inverted.notificationWhitelist)
        assertEquals(latest, inverted.copy(notificationWhitelist = latest.notificationWhitelist))
    }
}
