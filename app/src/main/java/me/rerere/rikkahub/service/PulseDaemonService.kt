package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.pulseIntent
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.pulse.PulseRuntimeStore
import me.rerere.rikkahub.service.pulse.batteryKeepRouteIntent
import me.rerere.rikkahub.service.pulse.PulseRuntime
import me.rerere.rikkahub.ui.pulse.PulseNotificationStrings
import me.rerere.rikkahub.ui.pulse.PulseNotificationText
import me.rerere.rikkahub.ui.pulse.renderPulseNotification
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class PulseDaemonService : Service() {
    private val settings: SettingsStore by inject()
    private val runtime: PulseRuntime by inject()
    private val handler = Handler(Looper.getMainLooper())
    private var startedElapsed = 0L
    private var stopping = false
    private var lastRendered: PulseNotificationText? = null
    private var lastBatteryActionVisible: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            if (stopping) return
            refreshNotification()
            handler.postDelayed(this, TICK_MS - (System.currentTimeMillis() % TICK_MS))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopping = false
        running = true
        instance = WeakReference(this)
        if (startedElapsed == 0L) {
            startedElapsed = SystemClock.elapsedRealtime()
            PulseRuntimeStore.update(this) { it.copy(serviceStartedAt = startedElapsed) }
        }
        ensureChannel()
        refreshNotification(force = true)
        handler.removeCallbacks(tick)
        handler.post(tick)
        if (!settings.settingsFlow.value.init && !settings.settingsFlow.value.pulse.keepAliveEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        runtime.trigger()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        stopping = true
        handler.removeCallbacks(tick)
        instance = null
        running = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.pulse_keepalive_title), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.pulse_daemon_notification) }
        )
    }

    private fun refreshNotification(force: Boolean = false) {
        if (stopping) return
        val state = PulseRuntimeStore.load(this)
        val text = renderPulseNotification(
            config = settings.settingsFlow.value.pulse,
            state = state,
            nowEpochMillis = System.currentTimeMillis(),
            nowElapsedMillis = SystemClock.elapsedRealtime(),
            strings = notificationStrings(),
            timeFormatter = { time -> android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date.from(time.toInstant())) },
        )
        val actionChanged = batteryActionVisibilityChanged()
        if (!force && text == lastRendered && !actionChanged) return
        lastRendered = text
        publishNotification(text)
    }

    private fun batteryActionVisibilityChanged(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val visible = power != null && !power.isIgnoringBatteryOptimizations(packageName)
        val changed = visible != lastBatteryActionVisible
        lastBatteryActionVisible = visible
        return changed
    }

    private fun notificationStrings() = PulseNotificationStrings(
        running = getString(R.string.pulse_daemon_title_running),
        heartbeat = getString(R.string.pulse_daemon_title_heartbeat),
        failed = getString(R.string.pulse_daemon_title_failed),
        idle = getString(R.string.pulse_daemon_title_idle),
        count = { count, time -> getString(R.string.pulse_daemon_line_count, count, time) },
        countTomorrow = { count, time -> getString(R.string.pulse_daemon_line_count_tomorrow, count, time) },
        countNone = { time -> getString(R.string.pulse_daemon_line_count_none, time) },
        runningLine = { count -> getString(R.string.pulse_daemon_line_running, count) },
        lastOfDay = { count, time -> getString(R.string.pulse_daemon_line_last_of_day, count, time) },
        lastOfDayRunning = { count -> getString(R.string.pulse_daemon_line_last_of_day_running, count) },
        idleLine = { duration -> getString(R.string.pulse_daemon_line_idle, duration) },
        durationHoursMinutes = { hours, minutes -> getString(R.string.pulse_daemon_duration_hm, hours, minutes) },
        durationMinutes = { minutes -> getString(R.string.pulse_daemon_duration_m, minutes) },
    )

    private fun publishNotification(text: PulseNotificationText) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, pulseIntent(this),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_rikkahub)
                .setContentTitle(getString(R.string.pulse_daemon_public_title))
                .setContentText(getString(R.string.pulse_daemon_public_text))
                .setShowWhen(false)
                .build())
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(packageName)) {
            builder.addAction(0, getString(R.string.pulse_keepalive_action_hold),
                PendingIntent.getActivity(this, BATTERY_REQUEST_CODE, batteryKeepRouteIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val notification = builder.build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { android.util.Log.e(TAG, "Unable to publish foreground notification", it) }
    }

    companion object {
        const val NOTIFICATION_ID = 2003
        private const val TAG = "PulseDaemon"
        private const val CHANNEL_ID = "pulse_daemon"
        private const val BATTERY_REQUEST_CODE = 2303
        private const val TICK_MS = 60_000L
        @Volatile private var instance: WeakReference<PulseDaemonService>? = null
        @Volatile var running: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PulseDaemonService::class.java))
        }

        fun stop(context: Context) { context.stopService(Intent(context, PulseDaemonService::class.java)) }

        fun refresh(context: Context) {
            instance?.get()?.let { service -> service.handler.post { service.refreshNotification() } }
        }
    }
}
