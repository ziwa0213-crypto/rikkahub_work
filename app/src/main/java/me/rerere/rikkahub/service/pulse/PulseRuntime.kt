package me.rerere.rikkahub.service.pulse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.conversationIntent
import me.rerere.rikkahub.pulseLogsIntent
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.repository.PulseRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.pulse.PulseRuntimeStore
import me.rerere.rikkahub.data.pulse.appendPulseActivityLog
import me.rerere.rikkahub.data.pulse.appendPulseRunLog
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.PulseDaemonService
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.rikkahub.utils.JsonInstant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.uuid.Uuid

private const val HEARTBEAT_RETRY_KEY = "heartbeat_retry_at"

/** App-owned scheduler. Alarm/Worker/foreground events are signals, not owners of generation. */
class PulseRuntime(
    private val context: Application,
    private val scope: AppScope,
    private val settingsStore: SettingsStore,
    private val repository: PulseRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val chat: Lazy<ChatService>,
) {
    private val storage = context.getSharedPreferences("pulse_runs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var active: Job? = null
    @Volatile private var foreground = false
    @Volatile private var heartbeatRetryAt = storage.getLong(HEARTBEAT_RETRY_KEY, 0L)
    private val claims = PulseClaims(
        load = { storage.getString("claim", null)?.let { JsonInstant.decodeFromString<PulseClaim>(it) } ?: PulseClaim() },
        save = { check(storage.edit().putString("claim", JsonInstant.encodeToString(it)).commit()) { "Cannot save Pulse claim" } },
    )
    private val effects = PulseEffects(
        load = { storage.getString(it, null) },
        save = { key, value -> check(storage.edit().putString(key, value).commit()) { "Cannot record Pulse write" } },
    )
    private val watcherSampler = PulseWatcherSampler(context, workspaceRepository)

    init {
        PulseNotificationInbox.settingsProvider = { settingsStore }
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    foreground = true
                    if (settingsStore.settingsFlow.value.pulse.keepAliveEnabled) startDaemon()
                    PulseDaemonService.refresh(context)
                    trigger()
                } else if (event == Lifecycle.Event.ON_STOP) foreground = false
            })
            foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        scope.launch {
            settingsStore.settingsFlow.map { it.init to it.pulse }.distinctUntilChanged().collect { (loading, config) ->
                if (!loading) {
                    PulseNotificationInbox.buffer.synchronize(config)
                    if (config.keepAliveEnabled && foreground) startDaemon()
                    if (!config.keepAliveEnabled) PulseDaemonService.stop(context)
                    rearm()
                    PulseDaemonService.refresh(context)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(60_000)
                if (foreground) trigger()
            }
        }
    }

    fun trigger(manual: Boolean = false) {
        scope.launch {
            mutex.withLock {
                if (active?.isActive == true) return@withLock
                active = scope.launch(Dispatchers.IO) {
                    try { runDue(manual) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        Log.e("Pulse", "Dispatch failed", error)
                        chat.value.addError(error, settingsStore.settingsFlow.value.pulse.conversationId, autoDismiss = false)
                    } finally { rearm() }
                }
            }
        }
    }

    private suspend fun runDue(manual: Boolean) {
        var config = settingsStore.awaitLoaded().pulse
        if (!foreground && !config.keepAliveEnabled) return
        if (!config.enableHeartbeat && !config.enableScheduledTasks && !(config.enableWatchers && config.watchers.any { it.enabled }) && !config.smsPollingEnabled()) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        // Recompute cron after a timezone change. Never make up missed occurrences.
        settingsStore.update { settings -> settings.copy(pulse = settings.pulse.copy(tasks = settings.pulse.tasks.map { task ->
            if (task.kind == PulseTaskKind.CRON && (task.timeZoneId != zone.id || task.nextAt == 0L)) {
                task.copy(nextAt = runCatching { PulseCron(task.cron).nextAfter(now, zone) }.getOrNull() ?: 0,
                    timeZoneId = zone.id)
            } else task
        })) }
        config = settingsStore.settingsFlow.value.pulse
        if (config.keepAliveEnabled && foreground) startDaemon()
        if (!manual && !pulseWithinHours(now, config.activeHoursStart, config.activeHoursEnd)) return
        val id = repository.ensurePulseConversation()
        if (conversationBusy(id)) return

        val pending = storage.getString("claim", null)?.let { JsonInstant.decodeFromString<PulseClaim>(it) }
        val interrupted = pending?.takeIf { claim ->
            claim.until <= now && claim.isPendingFor(config)
        }
        val interval = config.heartbeatIntervalMinutes.coerceIn(1, 240) * 60_000L
        var heartbeatRan = false
        val heartbeatDue = config.enableHeartbeat && !interrupted?.key.orEmpty().startsWith("task:") &&
            (manual || now >= heartbeatRetryAt) &&
            (manual || config.lastHeartbeatAt == 0L || pulseHeartbeatDue(now, config.lastHeartbeatAt, interval))
        val heartbeatSlot = when {
            interrupted?.key?.startsWith("heartbeat:") == true -> interrupted.key.substringAfter(':').toLongOrNull()
            heartbeatDue -> now
            else -> null
        }
        if (heartbeatSlot != null) {
            heartbeatRan = execute(id, "heartbeat:$heartbeatSlot", "heartbeat", null,
                config.heartbeatPrompt.ifBlank { DEFAULT_HEARTBEAT_PROMPT }, heartbeatSlot)
        }
        config = settingsStore.settingsFlow.value.pulse
        if (config.enableWatchers) sampleDueWatchers(id, config)
        var count = 0
        val tasks = settingsStore.settingsFlow.value.pulse.tasks.filter { config.enableScheduledTasks }
            .sortedBy { if (interrupted?.key?.startsWith("task:${it.id}:") == true) 0 else 1 }
        for (task in tasks) {
            if (!task.enabled || count >= 10) continue
            val tickNow = System.currentTimeMillis()
            val latest = settingsStore.settingsFlow.value.pulse
            if (!latest.enableScheduledTasks || !pulseWithinHours(tickNow, latest.activeHoursStart, latest.activeHoursEnd)) break
            if (latest.tasks.none { it.id == task.id && it.enabled }) continue
            val recoveredSlot = interrupted?.key?.takeIf { it.startsWith("task:${task.id}:") }?.substringAfterLast(':')?.toLongOrNull()
            val slot = recoveredSlot ?: when (task.kind) {
                PulseTaskKind.ONCE -> task.dueAt.takeIf { it <= tickNow }
                PulseTaskKind.CRON -> task.nextAt.takeIf { it > 0 && it <= tickNow }
                PulseTaskKind.ON_HEARTBEAT -> heartbeatSlot?.takeIf { heartbeatRan }
            } ?: continue
            if (task.lastSlot >= slot) continue
            if (recoveredSlot == null && task.kind == PulseTaskKind.ONCE && tickNow - slot > 6 * 3_600_000L) {
                updateTask(task.id) { it.copy(enabled = false, lastResult = "EXPIRED") }
                continue
            }
            if (recoveredSlot == null && task.kind == PulseTaskKind.CRON && tickNow - slot >= 60_000) {
                updateTask(task.id) { it.copy(nextAt = PulseCron(it.cron).nextAfter(tickNow, zone) ?: 0) }
                continue
            }
            if (execute(id, "task:${task.id}:$slot", task.title, task, task.prompt, slot)) count++
        }
        if (!conversationBusy(id)) maybePollSms(id)
    }

    private suspend fun maybePollSms(id: Uuid) {
        val config = settingsStore.settingsFlow.value.pulse
        val now = System.currentTimeMillis()
        val state = PulseRuntimeStore.load(context)
        if (smsNextPollAt(config, state.lastSmsPollAt, now) > now) return
        PulseRuntimeStore.update(context) { it.copy(lastSmsPollAt = now) }
        if (!me.rerere.rikkahub.data.pulse.SmsReader.hasPermission(context)) return
        try {
            if (!state.smsBaselineReady && config.smsWatermarkId == 0L) {
                val baseline = me.rerere.rikkahub.data.pulse.SmsReader.currentMaxId(context)
                settingsStore.update { it.copy(pulse = it.pulse.copy(smsWatermarkId = baseline)) }
                PulseRuntimeStore.update(context) { it.copy(smsBaselineReady = true) }
                return
            }
            PulseRuntimeStore.update(context) { it.copy(smsBaselineReady = true) }
            val discovery = me.rerere.rikkahub.data.pulse.SmsReader.discover(context, config.smsWatermarkId, config.smsSenderWhitelist)
            val latest = settingsStore.settingsFlow.value.pulse
            if (!latest.smsPollingEnabled() || latest.smsSenderWhitelist != config.smsSenderWhitelist) return
            if (discovery.senders.isEmpty()) {
                settingsStore.update { it.copy(pulse = it.pulse.copy(smsWatermarkId = maxOf(it.pulse.smsWatermarkId, discovery.maxScannedId))) }
                return
            }
            val instruction = context.getString(R.string.pulse_sms_discovered, discovery.senders.size,
                discovery.senders.distinct().take(20).joinToString(", "))
            val hint = context.getString(R.string.pulse_sensing_summary_sms, discovery.senders.size, discovery.senderCount)
            execute(id, "sms:${discovery.maxScannedId}:$now", context.getString(R.string.pulse_sms_read_title), null,
                instruction, discovery.maxScannedId, eventKind = "sms", summaryHint = hint)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Provider/ROM errors may contain phone numbers. Keep diagnostics free of SMS data.
            Log.w("Pulse", "SMS poll unavailable; check system permissions")
        }
    }

    /** True only when the turn was attempted; a claimed but skipped heartbeat did not run. */
    private suspend fun execute(
        id: Uuid,
        key: String,
        title: String,
        task: PulseTask?,
        prompt: String,
        slot: Long,
        watcher: PulseWatcher? = null,
        eventKind: String? = null,
        summaryHint: String? = null,
    ): Boolean {
        if (conversationBusy(id)) return false
        val claim = mutex.withLock { claims.claim(key, System.currentTimeMillis(), false) } ?: return false
        val start = SystemClock.uptimeMillis()
        var startedAt = System.currentTimeMillis()
        var status = "FAILED"
        var summary = ""
        var promptTokens = 0
        var completionTokens = 0
        var cachedTokens = 0
        val heartbeat = task == null && watcher == null && eventKind == null
        // Preserve failure privacy if the user disables sensing while this turn is running.
        val initialConfig = settingsStore.settingsFlow.value.pulse
        val sensingEnabledAtStart = initialConfig.enableNotificationReading || initialConfig.enableSmsReading || initialConfig.enableSmsSending
        // Old tool results can be paraphrased in later turns, even after the switches are disabled.
        val sensingHistory = pulseHasSensingData(chat.value.getConversation(id)?.currentMessages.orEmpty(), eventKind)
        val kind = when {
            eventKind != null -> eventKind
            heartbeat -> "heartbeat"
            watcher != null -> "watcher"
            else -> "task"
        }
        var nextHeartbeat: Pair<Long, Boolean>? = null
        PulseRuntimeStore.update(context) { state ->
            state.copy(
                runningSince = startedAt,
                runningLabel = if (heartbeat) "heartbeat" else kind,
                nextHeartbeatAt = state.nextHeartbeatAt,
                lastOfDay = state.lastOfDay,
            )
        }
        PulseDaemonService.refresh(context)
        try {
            coroutineScope {
                val renewal = launch {
                    while (isActive) {
                        delay(PulseClaims.RENEW_MS)
                        check(claims.renew(claim, System.currentTimeMillis())) { "Pulse execution ownership changed" }
                    }
                }
                try {
                    val now = ZonedDateTime.now()
                    val contextPrompt = "$prompt\n\n## Context\nCurrent local time: $now (${now.zone.id})"
                    val result = chat.value.runPulseTurn(
                        id, contextPrompt, toolContext(key, claim), skipIfUnanswered = heartbeat || eventKind == "sms",
                    ) {
                        if (eventKind == "sms") settingsStore.update { it.copy(pulse = it.pulse.copy(smsWatermarkId = maxOf(it.pulse.smsWatermarkId, slot))) }
                        if (heartbeat) {
                            startedAt = System.currentTimeMillis()
                            val current = settingsStore.settingsFlow.value.pulse
                            nextHeartbeat = pulseNextHeartbeatAt(
                                startedAt,
                                current.heartbeatIntervalMinutes.coerceIn(1, 240) * 60_000L,
                                current,
                            )
                            setHeartbeatRetryAt(0L)
                            settingsStore.update { it.copy(pulse = it.pulse.copy(lastHeartbeatAt = startedAt)) }
                            PulseRuntimeStore.update(context) { state -> state.copy(lastHeartbeatAt = startedAt) }
                        }
                    }
                    if (result == null) {
                        status = "SKIPPED"
                        // Recheck pending input without changing the last actual heartbeat start.
                        if (heartbeat) setHeartbeatRetryAt(System.currentTimeMillis() + PULSE_HEARTBEAT_RECHECK_MS)
                        summary = context.getString(R.string.pulse_turn_skipped_pending)
                        recordActivity(kind, watcher?.id ?: task?.id.orEmpty(), status, summary, "")
                    } else {
                        result.response.lastRequestUsage()?.let { usage ->
                            val totalUsage = result.response.usage ?: usage
                            promptTokens = totalUsage.promptTokens.coerceAtLeast(0)
                            completionTokens = totalUsage.completionTokens.coerceAtLeast(0)
                            cachedTokens = totalUsage.cachedTokens.coerceAtLeast(0)
                        }
                        val silent = heartbeat && !result.awaitingApproval && isSilentPulse(result.text)
                        if (!silent || !settingsStore.settingsFlow.value.pulse.heartbeatSilentWhenNoContent) {
                            chat.value.persistPulseTurn(result)
                        }
                        status = if (result.awaitingApproval) "WAITING_APPROVAL" else if (silent) "OK" else "REPORTED"
                        val sensitive = sensingHistory || pulseHasSensingData(listOf(result.response), eventKind)
                        summary = when {
                            result.awaitingApproval -> context.getString(R.string.pulse_approval_waiting)
                            sensitive -> summaryHint ?: context.getString(R.string.pulse_sensing_summary_tools)
                            else -> result.text.lineSequence().firstOrNull().orEmpty().take(60)
                        }
                        val report = if (sensitive || result.awaitingApproval) "" else writeReport(kind, watcher?.id ?: task?.id.orEmpty(), title, result, status,
                            SystemClock.uptimeMillis() - start)
                        if (!silent) {
                            recordActivity(kind, watcher?.id ?: task?.id.orEmpty(), status, summary, report)
                            if (settingsStore.settingsFlow.value.pulse.notifyOnReport) notify(id, title, summary, false, report)
                        } else {
                            recordActivity(kind, watcher?.id.orEmpty(), status, summary, report)
                        }
                    }
                } finally { renewal.cancelAndJoin() }
            }
        } catch (cancelled: CancellationException) {
            status = "CANCELLED"
            summary = ""
            recordActivity(kind, watcher?.id ?: task?.id.orEmpty(), status, "", "")
            throw cancelled
        } catch (error: Exception) {
            // A failed turn may have consumed sensing data before it could return its tool history.
            // Switches guard only failure details, never successful activity summaries.
            val config = settingsStore.settingsFlow.value.pulse
            val sensitiveFailure = sensingHistory || sensingEnabledAtStart || config.enableNotificationReading || config.enableSmsReading || config.enableSmsSending
            summary = if (sensitiveFailure) context.getString(R.string.pulse_sensing_failure)
                else error.message?.takeIf { it.isNotBlank() }?.take(200)
                ?: context.getString(R.string.pulse_failure_unknown)
            val report = if (sensitiveFailure) "" else runCatching { writeFailureReport(kind, watcher?.id ?: task?.id.orEmpty(), title, summary) }
                .onFailure { Log.w("Pulse", "Failed to write failure report", it) }
                .getOrDefault("")
            recordActivity(kind, watcher?.id ?: task?.id.orEmpty(), "FAILED", summary, report)
            notify(id, title, summary, true, report)
        } finally {
            withContext(NonCancellable) {
                // A successor owns the state after takeover; stale completion cannot write its status.
                if (claims.renew(claim, System.currentTimeMillis())) {
                    val finishedAt = System.currentTimeMillis()
                    if (heartbeat) settingsStore.update { current -> current.copy(pulse = current.pulse.copy(
                        heartbeatLog = (listOf(HeartbeatLogEntry(finishedAt, status, summary.take(60))) + current.pulse.heartbeatLog).take(5),
                    )) }
                    else if (task != null) updateTask(task.id) { latest -> latest.copy(
                        lastRunAt = finishedAt, lastResult = if (status == "FAILED") "FAILED:$summary" else status,
                        lastSlot = slot,
                        enabled = latest.enabled && latest.kind != PulseTaskKind.ONCE,
                        nextAt = if (latest.kind == PulseTaskKind.CRON) PulseCron(latest.cron).nextAfter(finishedAt) ?: 0 else latest.nextAt,
                    ) }
                    val finalConfig = settingsStore.settingsFlow.value.pulse
                    val skippedHeartbeatNext = if (heartbeat && status == "SKIPPED" && finalConfig.enableHeartbeat) {
                        val interval = finalConfig.heartbeatIntervalMinutes.coerceIn(1, 240) * 60_000L
                        pulseNextHeartbeatAt(
                            finalConfig.lastHeartbeatAt.takeIf { it > 0 } ?: (finishedAt - interval),
                            interval,
                            finalConfig,
                            notBefore = maxOf(finishedAt, heartbeatRetryAt),
                        )
                    } else null
                    PulseRuntimeStore.update(context) { state ->
                        val successfulHeartbeat = heartbeat && status in setOf("OK", "REPORTED")
                        state.copy(
                            todaySuccessCount = if (successfulHeartbeat && status != "SKIPPED") state.todaySuccessCount + 1 else state.todaySuccessCount,
                            lastHeartbeatOk = when {
                                !heartbeat || status in setOf("CANCELLED", "SKIPPED", "WAITING_APPROVAL") -> state.lastHeartbeatOk
                                else -> successfulHeartbeat
                            },
                            runningSince = 0L,
                            runningLabel = "",
                            nextHeartbeatAt = if (heartbeat) {
                                skippedHeartbeatNext?.first ?: nextHeartbeat?.first ?: state.nextHeartbeatAt
                            } else state.nextHeartbeatAt,
                            lastOfDay = if (heartbeat) {
                                skippedHeartbeatNext?.second ?: nextHeartbeat?.second ?: state.lastOfDay
                            } else state.lastOfDay,
                        )
                    }
                    claims.finish(claim)
                    PulseDaemonService.refresh(context)
                    Log.i("Pulse", "run=$key result=$status durationMs=${SystemClock.uptimeMillis() - start}")
                    recordRunLog(PulseRunLog(
                        at = finishedAt,
                        kind = kind,
                        refId = watcher?.id ?: task?.id.orEmpty(),
                        result = if (status == "FAILED") "FAILED:$summary" else status,
                        durationMs = SystemClock.uptimeMillis() - start,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        cachedTokens = cachedTokens,
                        note = if (status == "FAILED") summary.take(200) else "",
                    ))
                }
            }
        }
        return status != "SKIPPED"
    }

    private suspend fun sampleDueWatchers(conversationId: Uuid, config: PulseConfig) {
        val now = System.currentTimeMillis()
        val due = duePulseWatchers(config.watchers, now)
        for (watcher in due) {
            if (conversationBusy(conversationId)) break
            val latestPulse = settingsStore.settingsFlow.value.pulse
            if (!latestPulse.enableWatchers) break
            val latest = latestPulse.watchers.firstOrNull { it.id == watcher.id } ?: continue
            if (!latest.enabled) continue
            val sample = try {
                watcherSampler.sample(latest, config.workspaceId?.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = error.message?.takeIf { it.isNotBlank() }?.take(200)
                    ?: context.getString(R.string.pulse_failure_unknown)
                val report = runCatching {
                    writeFailureReport("watcher", latest.id, latest.name.ifBlank { "Watcher" }, failure)
                }.onFailure { Log.w("Pulse", "Failed to write watcher failure report", it) }
                    .getOrDefault("")
                updateWatcher(latest.id) { it.copy(lastCheckedAt = now, lastResult = "FAILED:${failure.take(160)}") }
                recordActivity("watcher", latest.id, "FAILED", failure.take(160), report)
                recordRunLog(PulseRunLog(at = now, kind = "watcher", refId = latest.id,
                    result = "FAILED", note = failure.take(200)))
                notify(conversationId, latest.name.ifBlank { "Watcher" },
                    failure.take(160), true, report)
                continue
            }

            if (latest.target == WatcherTarget.BATTERY) {
                val current = sample.batteryPercent ?: continue
                val battery = watcherBatteryResult(latest.baselineBattery, current, latest.threshold)
                val first = latest.baselineBattery < 0
                updateWatcher(latest.id) { it.copy(
                    baselineBattery = battery.baseline,
                    lastFingerprint = sample.fingerprint,
                    lastCheckedAt = now,
                    lastChangedAt = if (battery.changed) now else it.lastChangedAt,
                    lastResult = if (battery.changed) "CHANGED" else "OK",
                ) }
                if (first || !battery.changed) {
                    recordActivity("watcher", latest.id, "OK", "", "")
                    recordRunLog(PulseRunLog(at = now, kind = "watcher", refId = latest.id, result = "OK"))
                    continue
                }
            } else {
                val changed = watcherChanged(latest.lastFingerprint, sample.fingerprint)
                updateWatcher(latest.id) { it.copy(
                    lastFingerprint = sample.fingerprint,
                    lastCheckedAt = now,
                    lastChangedAt = if (changed) now else it.lastChangedAt,
                    lastResult = if (changed) "CHANGED" else "OK",
                ) }
                if (!changed) {
                    recordActivity("watcher", latest.id, "OK", "", "")
                    recordRunLog(PulseRunLog(at = now, kind = "watcher", refId = latest.id, result = "OK"))
                    continue
                }
            }

            val prompt = buildString {
                appendLine(latest.prompt.ifBlank { "Summarize this monitored change and say whether I need to act." })
                appendLine()
                appendLine("Change detected: ${latest.name}")
                appendLine("Previous fingerprint: ${latest.lastFingerprint.take(16).ifBlank { "first sample" }}")
                appendLine("Current fingerprint: ${sample.fingerprint}")
                if (latest.target != WatcherTarget.BATTERY) appendLine(sample.preview.take(PulseWatcherSampler.PREVIEW_CHARS))
                else appendLine(sample.preview)
            }
            val slot = now
            execute(
                id = conversationId,
                key = "watcher:${latest.id}:$slot",
                title = latest.name.ifBlank { "Monitor changed" },
                task = null,
                prompt = prompt,
                slot = slot,
                watcher = latest,
            )
        }
    }

    private suspend fun updateWatcher(id: String, transform: (PulseWatcher) -> PulseWatcher) {
        settingsStore.update { settings -> settings.copy(pulse = settings.pulse.copy(watchers = settings.pulse.watchers.map { if (it.id == id) transform(it) else it })) }
    }

    private suspend fun recordActivity(kind: String, refId: String, result: String, summary: String, reportPath: String) {
        settingsStore.update { settings ->
            val entry = PulseActivityEntry(at = System.currentTimeMillis(), kind = kind, refId = refId,
                result = result, summary = summary, reportPath = reportPath)
            settings.copy(pulse = settings.pulse.copy(activityLog = appendPulseActivityLog(settings.pulse.activityLog, entry)))
        }
    }

    private suspend fun recordRunLog(log: PulseRunLog) {
        settingsStore.update { settings ->
            settings.copy(pulse = settings.pulse.copy(runLogs = appendPulseRunLog(settings.pulse.runLogs, log)))
        }
    }

    private suspend fun writeReport(
        kind: String,
        refId: String,
        title: String,
        result: PulseTurnResult,
        status: String,
        durationMs: Long,
    ): String = writeReportFile(pulseReportFileName(System.currentTimeMillis(), status), buildString {
        appendLine("Time: ${ZonedDateTime.now()}")
        appendLine("Type: $kind")
        appendLine("Name: $title")
        appendLine("Reference: $refId")
        appendLine("Duration: ${durationMs}ms")
        appendLine("Tools: ${result.response.getTools().joinToString { it.toolName }.ifBlank { "none" }}")
        appendLine()
        appendLine(result.text)
    })

    private suspend fun writeFailureReport(kind: String, refId: String, title: String, failure: String): String =
        writeReportFile(pulseReportFileName(System.currentTimeMillis(), "FAILED"), buildString {
            appendLine("Time: ${ZonedDateTime.now()}")
            appendLine("Type: $kind")
            appendLine("Name: $title")
            appendLine("Reference: $refId")
            appendLine("Result: FAILED")
            appendLine(failure.take(200))
        })

    private suspend fun writeReportFile(requestedName: String, content: String): String {
        val workspaceId = settingsStore.settingsFlow.value.pulse.workspaceId?.toString() ?: return ""
        if (workspaceRepository.getById(workspaceId)?.owner != "pulse") return ""
        return runCatching {
            val existing = runCatching { workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, "reports") }
                .getOrDefault(emptyList())
            var candidate = requestedName
            var sequence = 2
            while (existing.any { it.name == candidate }) {
                val stem = requestedName.removeSuffix(".txt")
                val status = stem.substringAfterLast('-')
                val prefix = stem.removeSuffix("-$status")
                candidate = "$prefix-$status-$sequence.txt"
                sequence++
            }
            workspaceRepository.writeText(workspaceId, "reports/$candidate", content, overwrite = false)
            val after = workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, "reports")
            pulseReportRetention(after.filterNot { it.isDirectory }.map { it.name }).forEach { name ->
                workspaceRepository.deleteFile(workspaceId, WorkspaceStorageArea.FILES, "reports/$name", recursive = false)
            }
            "reports/$candidate"
        }.onFailure { Log.w("Pulse", "Could not write report", it) }.getOrDefault("")
    }

    private fun watcherNextAt(watcher: PulseWatcher, now: Long): Long =
        if (watcher.lastCheckedAt == 0L) now else watcher.lastCheckedAt + watcher.everyMinutes.coerceIn(10, 240) * 60_000L

    private fun toolContext(run: String, claim: PulseClaim) = PulseToolContext { name, args, block ->
        check(claims.renew(claim, System.currentTimeMillis())) { "Pulse execution ownership changed" }
        effects.execute(run, name, args, block)
    }

    private fun setHeartbeatRetryAt(value: Long) {
        heartbeatRetryAt = value
        check(storage.edit().putLong(HEARTBEAT_RETRY_KEY, value).commit()) { "Cannot save heartbeat retry time" }
    }

    private suspend fun updateTask(id: String, transform: (PulseTask) -> PulseTask) {
        settingsStore.update { it.copy(pulse = it.pulse.copy(tasks = it.pulse.tasks.map { task -> if (task.id == id) transform(task) else task })) }
    }

    private suspend fun rearm() {
        runCatching { rearmUnchecked() }.onFailure { Log.w("Pulse", "Could not rearm background checks", it) }
    }

    private fun startDaemon() {
        if (!PulseDaemonService.running) runCatching { PulseDaemonService.start(context) }
            .onFailure { Log.w("Pulse", "Background service start deferred until foreground", it) }
    }

    private suspend fun rearmUnchecked() {
        val config = settingsStore.settingsFlow.value.pulse
        if (!config.keepAliveEnabled || (!config.enableHeartbeat && !config.enableScheduledTasks && !config.enableWatchers && !config.smsPollingEnabled())) {
            PulseScheduler.cancel(context)
            return
        }
        val now = System.currentTimeMillis()
        val interval = config.heartbeatIntervalMinutes.coerceIn(1, 240) * 60_000L
        val nextHeartbeat = if (config.enableHeartbeat) pulseNextHeartbeatAt(
            config.lastHeartbeatAt.takeIf { it > 0 } ?: (now - interval),
            interval,
            config,
            notBefore = maxOf(now, heartbeatRetryAt),
        ) else null
        val heartbeat = nextHeartbeat?.first ?: Long.MAX_VALUE
        var runtimeChanged = false
        PulseRuntimeStore.update(context) { state ->
            if (state.runningLabel == "heartbeat") state
            else state.copy(
                    nextHeartbeatAt = nextHeartbeat?.first ?: 0L,
                    lastOfDay = nextHeartbeat?.second ?: false,
                ).also { runtimeChanged = it != state }
        }
        if (runtimeChanged) PulseDaemonService.refresh(context)
        val task = config.tasks.filter { config.enableScheduledTasks && it.enabled && it.kind != PulseTaskKind.ON_HEARTBEAT }
            .minOfOrNull { if (it.kind == PulseTaskKind.ONCE) it.dueAt else it.nextAt.takeIf { at -> at > 0 } ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
        val watcher = config.watchers.filter { config.enableWatchers && it.enabled }
            .minOfOrNull { watcherNextAt(it, now) } ?: Long.MAX_VALUE
        val sms = smsNextPollAt(config, PulseRuntimeStore.load(context).lastSmsPollAt, now)
        val pending = storage.getString("claim", null)?.let { JsonInstant.decodeFromString<PulseClaim>(it) }
        val recovery = pending?.takeIf { it.isPendingFor(config) }?.until ?: Long.MAX_VALUE
        val candidate = pulseCandidateAfterLease(pending, now, minOf(heartbeat, watcher, task, sms, recovery))
        val generationActive = config.conversationId?.let { conversationBusy(it) } == true
        // User turns can outlive a due alarm. Keep retries bounded instead of scheduling
        // the same already-due watcher/task again every second while generation is active.
        val target = pulseDispatchTarget(candidate, now, generationActive)
        if (target != Long.MAX_VALUE) PulseScheduler.schedule(context,
            pulseNextActiveAt(target, config.activeHoursStart, config.activeHoursEnd))
    }

    private suspend fun conversationBusy(id: Uuid): Boolean =
        chat.value.getGenerationJobStateFlow(id).firstOrNull()?.isActive == true ||
            chat.value.getConversation(id)?.isAwaitingToolApproval == true

    private fun notify(id: Uuid, title: String, message: String, failure: Boolean, reportPath: String = "") {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("pulse", context.getString(R.string.group_mode_pulse), NotificationManager.IMPORTANCE_DEFAULT))
        val workspaceId = settingsStore.settingsFlow.value.pulse.workspaceId?.toString()
        val target = if (reportPath.isNotBlank() && workspaceId != null) {
            me.rerere.rikkahub.pulseReportIntent(context, workspaceId, reportPath)
        } else if (failure) pulseLogsIntent(context) else conversationIntent(context, id.toString())
        val notificationId = if (failure) 2005 else 2004
        val intent = PendingIntent.getActivity(context, notificationId, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(notificationId, NotificationCompat.Builder(context, "pulse")
                .setSmallIcon(R.drawable.ic_stat_rikkahub).setContentTitle(context.getString(R.string.group_mode_pulse) + " · " + title)
                .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(if (failure) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(if (failure) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_MESSAGE)
                .setColor(if (failure) android.graphics.Color.RED else android.graphics.Color.TRANSPARENT)
                .setContentIntent(intent).setAutoCancel(true).build())
        } catch (denied: SecurityException) { Log.w("Pulse", "Notification permission denied", denied) }
    }
}

