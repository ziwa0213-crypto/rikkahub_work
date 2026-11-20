package me.rerere.rikkahub.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppRuntime
import me.rerere.llamacpp.LocalModelState
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.LOCAL_MODEL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.receiver.LocalModelUnloadReceiver
import java.util.Locale

/** Advisory only: a hidden app never unloads without a user's notification action. */
class LocalModelIdleWatcher(private val context: Context, private val runtime: LlamaCppRuntime,
    private val settings: SettingsStore, private val scope: AppScope) {
    @Volatile private var hidden = false
    private var job: Job? = null
    private val manager get() = NotificationManagerCompat.from(context)

    fun onAppHidden() {
        hidden = true
        job?.cancel()
        job = scope.launch {
            delay(30_000)
            combine(runtime.state, runtime.generatingState, settings.settingsFlow) { state, generating, current ->
                Triple(state, generating, current)
            }.collect { (state, generating, current) ->
                if (hidden && state is LocalModelState.Ready && !current.init && !runtime.hasPendingUnload)
                    show(state, generating, current)
                else cancelNotification()
            }
        }
    }

    fun onAppVisible() {
        hidden = false
        job?.cancel(); job = null
        runtime.cancelPendingUnload()
        cancelNotification()
    }

    private fun usedByPulse(state: LocalModelState.Ready, settings: Settings): Boolean {
        val pulse = settings.pulse
        val enabled = pulse.enableHeartbeat || pulse.enableScheduledTasks || pulse.enableWatchers || pulse.smsPollEnabled
        val model = settings.findModelById(pulse.chatModelId ?: settings.chatModelId)
        return enabled && model?.modelId == state.modelId && model.findProvider(settings.providers) is ProviderSetting.LlamaCppLocal
    }

    private fun show(state: LocalModelState.Ready, generating: Boolean, settings: Settings) {
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val retained = usedByPulse(state, settings)
        val title = context.getString(if (generating) R.string.local_llm_background_generating else R.string.local_llm_background_title)
        val body = when {
            retained -> context.getString(R.string.local_llm_background_retained)
            generating -> context.getString(R.string.local_llm_background_after_generation)
            else -> context.getString(R.string.local_llm_background_memory, String.format(Locale.getDefault(), "%.1f", state.bytes / 1_000_000_000.0))
        }
        val builder = NotificationCompat.Builder(context, LOCAL_MODEL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, ID, Intent(context, RouteActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(false).setOngoing(false).setSilent(true).setOnlyAlertOnce(true)
        if (!retained) builder.addAction(0, context.getString(if (generating) R.string.local_llm_unload_after_generation else R.string.local_llm_unload),
            PendingIntent.getBroadcast(context, ID, Intent(context, LocalModelUnloadReceiver::class.java)
                .putExtra("modelId", state.modelId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        runCatching { manager.notify(ID, builder.build()) }
    }

    suspend fun requestUnload(modelId: String?) {
        val ready = runtime.state.value as? LocalModelState.Ready ?: return
        if (!hidden || ready.modelId != modelId || usedByPulse(ready, settings.settingsFlow.value)) return
        if (runtime.hasPendingUnload) {
            Toast.makeText(context, R.string.local_llm_unload_queued, Toast.LENGTH_SHORT).show()
            return
        }
        if (runtime.requestUnloadWhenIdle()) {
            // A foreground transition may have arrived while waiting for inference.
            runtime.unloadIf { hidden && (runtime.state.value as? LocalModelState.Ready)?.modelId == modelId }
        }
        cancelNotification()
    }

    private fun cancelNotification() { manager.cancel(ID) }
    private companion object { const val ID = 2101 }
}
