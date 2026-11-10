package me.rerere.rikkahub.service.pulse

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PulseNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        PulseNotificationInbox.connected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        PulseNotificationInbox.connected = false
        PulseNotificationInbox.buffer.clear()
    }

    override fun onDestroy() {
        PulseNotificationInbox.connected = false
        PulseNotificationInbox.buffer.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val settings = PulseNotificationInbox.settingsProvider?.invoke()?.settingsFlow?.value ?: return
        if (settings.init || !settings.pulse.enableNotificationReading) return
        val notification = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing ||
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        PulseNotificationInbox.buffer.offer(
            settings.pulse, packageName, sbn.packageName, label, sbn.postTime,
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }
}
