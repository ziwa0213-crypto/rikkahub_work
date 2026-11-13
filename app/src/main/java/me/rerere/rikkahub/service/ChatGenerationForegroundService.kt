package me.rerere.rikkahub.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.PULSE_RUN_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.conversationIntent
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

private const val TAG = "ChatGenerationFgs"

/**
 * Keeps the app process in the foreground while one or more chat generations are active.
 *
 * Generation itself remains owned by [ChatService]. This service only provides the Android
 * foreground-service lifetime required for streaming to continue after the activity is hidden.
 */
class ChatGenerationForegroundService : Service() {
    companion object {
        private const val ACTION_ACQUIRE = "me.rerere.rikkahub.action.CHAT_GENERATION_ACQUIRE"
        private const val ACTION_RELEASE = "me.rerere.rikkahub.action.CHAT_GENERATION_RELEASE"
        private const val EXTRA_GENERATION_ID = "generation_id"
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val WAKE_LOCK_TAG = "rikkahub:generation"
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_IS_PULSE = "is_pulse"

        const val NOTIFICATION_ID = 2002

        fun acquire(
            context: Context,
            generationId: Uuid,
            conversationId: Uuid,
            activity: SessionActivity = SessionActivity.Generating,
            isPulse: Boolean = false,
        ): Boolean {
            val intent = Intent(context, ChatGenerationForegroundService::class.java).apply {
                action = ACTION_ACQUIRE
                putExtra(EXTRA_GENERATION_ID, generationId.toString())
                putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
                putExtra(EXTRA_ACTIVITY, activity.name)
                putExtra(EXTRA_IS_PULSE, isPulse)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure {
                Log.e(TAG, "Unable to start chat generation foreground service", it)
            }.getOrDefault(false)
        }

        fun release(context: Context, generationId: Uuid) {
            val intent = Intent(context, ChatGenerationForegroundService::class.java).apply {
                action = ACTION_RELEASE
                putExtra(EXTRA_GENERATION_ID, generationId.toString())
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.e(TAG, "Unable to release chat generation foreground service", it)
            }
        }
    }

    private data class ActiveGeneration(val conversationId: String, val activity: SessionActivity, val isPulse: Boolean)

    private val activeGenerations = linkedMapOf<String, ActiveGeneration>()
    private var isForeground = false
    private var isStopping = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val renewHandler = Handler(Looper.getMainLooper())
    private val wakeLockRenewal = GenerationWakeLockRenewal(
        isActive = { !isStopping && isForeground && activeGenerations.isNotEmpty() },
        renew = ::ensureWakeLock,
        postDelayed = { callback, delayMs -> renewHandler.postDelayed(callback, delayMs) },
        removeCallbacks = renewHandler::removeCallbacks,
    )
    private val appScope: AppScope by inject()
    private val chatService: ChatService by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACQUIRE -> acquire(intent, startId)
            ACTION_RELEASE -> release(intent, startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isStopping = true
        wakeLockRenewal.stop()
        releaseWakeLock()
        activeGenerations.clear()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        isStopping = true
        wakeLockRenewal.stop()
        releaseWakeLock()
        Log.e(TAG, "Foreground service timed out (type=$fgsType)")
        activeGenerations.values
            .mapNotNull { runCatching { Uuid.parse(it.conversationId) }.getOrNull() }
            .distinct()
            .forEach { conversationId ->
                appScope.launch {
                    chatService.stopGeneration(conversationId)
                }
            }
        // Android only allows a few seconds after onTimeout before raising RemoteServiceException.
        stopService()
    }

    private fun acquire(intent: Intent, startId: Int) {
        val generationId = intent.getStringExtra(EXTRA_GENERATION_ID) ?: return stopSelf(startId)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return stopSelf(startId)
        val activity = SessionActivity.entries.find { it.name == intent.getStringExtra(EXTRA_ACTIVITY) }
            ?: SessionActivity.Generating
        val generation = ActiveGeneration(conversationId, activity, intent.getBooleanExtra(EXTRA_IS_PULSE, false))
        activeGenerations[generationId] = generation
        updateForegroundNotification(notificationGeneration())
        wakeLockRenewal.start()
    }

    private fun release(intent: Intent, startId: Int) {
        intent.getStringExtra(EXTRA_GENERATION_ID)?.let(activeGenerations::remove)
        if (activeGenerations.isEmpty()) {
            wakeLockRenewal.stop()
            releaseWakeLock()
            // Only stop if no newer start request is queued. A pending ACQUIRE came from
            // startForegroundService(), and stopping before it calls startForeground() crashes
            // with ForegroundServiceDidNotStartInTimeException. onDestroy() removes the notification.
            stopSelf(startId)
        } else {
            updateForegroundNotification(notificationGeneration())
            wakeLockRenewal.start()
        }
    }

    private fun ensureWakeLock() {
        if (isStopping || !isForeground || activeGenerations.isEmpty()) return
        val lock = runCatching {
            wakeLock ?: (getSystemService(PowerManager::class.java) ?: return)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
        }.onFailure {
            Log.w(TAG, "Unable to create generation wake lock", it)
        }.getOrNull() ?: return
        // A failed release must not skip reacquisition; the bounded lease remains a safety net.
        runCatching {
            if (lock.isHeld) lock.release()
        }.onFailure {
            Log.w(TAG, "Unable to release generation wake lock before renewal", it)
        }
        runCatching {
            // Also reacquires an expired lease; a non-null reference does not mean isHeld.
            lock.acquire(GENERATION_WAKE_LOCK_LEASE_MS)
        }.onFailure {
            Log.w(TAG, "Unable to acquire generation wake lock", it)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        runCatching { if (lock?.isHeld == true) lock.release() }
            .onFailure { Log.w(TAG, "Unable to release generation wake lock", it) }
    }

    private fun updateForegroundNotification(generation: ActiveGeneration) {
        if (isStopping) return
        try {
            val notification = buildNotification(generation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground", e)
            activeGenerations.clear()
            stopService()
        }
    }

    private fun stopService() {
        isStopping = true
        wakeLockRenewal.stop()
        releaseWakeLock()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    private fun notificationGeneration(): ActiveGeneration = activeGenerations.values.lastOrNull { !it.isPulse }
        ?: activeGenerations.values.last()

    private fun buildNotification(generation: ActiveGeneration) =
        NotificationCompat.Builder(this, if (generation.isPulse) PULSE_RUN_NOTIFICATION_CHANNEL_ID else CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    if (generation.isPulse) R.string.notification_pulse_running else when (generation.activity) {
                        SessionActivity.Generating -> R.string.notification_live_update_title
                        SessionActivity.Compressing -> R.string.chat_page_compressing
                    }
                )
            )
            .setContentIntent(getConversationPendingIntent(generation.conversationId))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(generation.isPulse)
            .build()

    private fun getConversationPendingIntent(conversationId: String): PendingIntent {
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            conversationIntent(this, conversationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
