package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.UsageTotals
import me.rerere.rikkahub.data.pulse.PulseRuntimeStore
import me.rerere.rikkahub.data.pulse.calculatePulseDailyCost
import me.rerere.rikkahub.service.pulse.pulseWithinHours
import me.rerere.rikkahub.service.pulse.pulseUpcomingTasks
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@Composable
fun PulseStatusRow(config: PulseConfig, compact: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val usageDao = koinInject<UsageRecordDAO>()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var runtimeState by remember(context) { mutableStateOf(PulseRuntimeStore.load(context)) }
    var dayStart by remember { mutableLongStateOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    LaunchedEffect(context) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            runtimeState = PulseRuntimeStore.load(context)
            val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (today != dayStart) dayStart = today
        }
    }
    val usage by remember(config, dayStart) {
        config.conversationId?.let { id ->
            usageDao.observeConversationTotals(id.toString(), dayStart).map { calculatePulseDailyCost(it, config, dayStart) }
        } ?: flowOf(calculatePulseDailyCost(UsageTotals(), config, dayStart))
    }.collectAsStateWithLifecycle(initialValue = calculatePulseDailyCost(UsageTotals(), config, dayStart))
    val next = runtimeState.nextHeartbeatAt.takeIf { it > 0 }
        ?: (config.lastHeartbeatAt.takeIf { it > 0 } ?: now) + config.heartbeatIntervalMinutes.coerceIn(1, 240) * 60_000L
    val withinHours = pulseWithinHours(now, config.activeHoursStart, config.activeHoursEnd)
    val failed = config.heartbeatLog.firstOrNull()?.result?.startsWith("FAILED") == true
    val statusColor = when {
        !config.enableHeartbeat || !withinHours -> MaterialTheme.colorScheme.onSurfaceVariant
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val text = when {
        !config.enableHeartbeat -> stringResource(R.string.pulse_status_off)
        !withinHours -> stringResource(R.string.pulse_status_paused,
            "%02d:00".format(config.activeHoursStart), "%02d:00".format(config.activeHoursEnd))
        failed -> stringResource(R.string.pulse_status_failed)
        else -> stringResource(R.string.pulse_status_running)
    }
    val upcoming = pulseUpcomingTasks(config.tasks, now)
    val pending = upcoming.size
    val sub = when {
        !config.enableHeartbeat -> null
        pending > 0 -> if (withinHours) stringResource(R.string.pulse_drawer_next, pulseTime(next), pending)
            else stringResource(R.string.pulse_drawer_tasks, pending)
        withinHours -> stringResource(R.string.pulse_drawer_next_only, pulseTime(next))
        else -> null
    }
    val animatedTokens by animateLongAsState(usage.totalTokens)
    val animatedCost by animateFloatAsState(
        targetValue = usage.costYuan?.toFloat() ?: 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "pulseCost",
    )
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        (fadeIn(tween(120, easing = LinearOutSlowInEasing)) togetherWith
                            fadeOut(tween(120, easing = LinearOutSlowInEasing))).using(null)
                    },
                    modifier = Modifier.weight(1f),
                    label = "pulseStatusText",
                ) { value ->
                    Text(value, style = MaterialTheme.typography.labelMedium, color = statusColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val usageText = pulseUsageLine(animatedTokens, usage.costYuan?.let { animatedCost.toDouble() },
            recent = false, costFractionDigits = 2)
        Text(usageText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!compact && config.enableHeartbeat) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.pulse_drawer_upcoming),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (upcoming.isEmpty()) {
                Text(stringResource(R.string.pulse_drawer_no_tasks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
            upcoming.take(3).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (item.onHeartbeat) stringResource(R.string.pulse_task_on_heartbeat)
                            else item.at?.let { pulseTimeShort(it, now) } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                    Text(item.title, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (upcoming.size > 3) {
                Text(stringResource(R.string.pulse_drawer_upcoming_more, upcoming.size - 3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The narrow upcoming list uses just the time for today's tasks. */
private fun pulseTimeShort(at: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return target.format(DateTimeFormatter.ofPattern(if (target.toLocalDate() == today) "HH:mm" else "MM-dd HH:mm"))
}

/** Animate a fraction instead of converting the token total to Float, preserving the final Long. */
@Composable
private fun animateLongAsState(targetValue: Long): State<Long> {
    val value = remember { mutableLongStateOf(targetValue) }
    LaunchedEffect(targetValue) {
        val start = value.longValue
        if (start != targetValue) {
            animate(0f, 1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) { fraction, _ ->
                value.longValue = if (fraction >= 1f) targetValue else
                    (start.toDouble() + (targetValue.toDouble() - start.toDouble()) * fraction).roundToLong()
            }
            value.longValue = targetValue
        }
    }
    return value
}
