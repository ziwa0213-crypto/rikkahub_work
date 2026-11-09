package me.rerere.rikkahub.service.pulse

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

object PulseScheduler {
    private const val UNIQUE_WORK = "pulse_dispatch"
    private fun intent(context: Context) = PendingIntent.getBroadcast(context, 4101,
        Intent(context, PulseAlarmReceiver::class.java).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, at: Long) {
        val time = at.coerceAtLeast(System.currentTimeMillis() + 1_000)
        val alarms = context.getSystemService(AlarmManager::class.java)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent(context))
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent(context))
        }
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PulseDispatchWorker>()
                .setInitialDelay((time - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
    }

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("pulse_signal", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PulseDispatchWorker>().build())
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}

/** Only dispatches into the app-owned runtime; a Worker deadline must not cancel generation. */
class PulseDispatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters), KoinComponent {
    private val runtime: PulseRuntime by inject()
    override suspend fun doWork(): Result {
        runtime.trigger()
        return Result.success()
    }
}

class PulseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { PulseScheduler.enqueue(context) }
}

class PulseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            // Never start a foreground service from this receiver.
            PulseScheduler.enqueue(context)
        }
    }
}
