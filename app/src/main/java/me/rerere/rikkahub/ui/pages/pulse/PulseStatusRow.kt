package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import me.rerere.rikkahub.data.pulse.calculatePulseCost
import me.rerere.rikkahub.data.pulse.compactPulseNumber
import me.rerere.rikkahub.service.pulse.pulseWithinHours
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PulseStatusRow(config: PulseConfig, onClick: () -> Unit) {
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
    val usage by remember(config.conversationId, dayStart, config.priceInputPerMillion, config.priceOutputPerMillion,
        config.priceCachedPerMillion, config.priceMultiplier) {
        config.conversationId?.let { id ->
            usageDao.observeConversationTotals(id.toString(), dayStart).map { calculatePulseCost(it, config) }
        } ?: flowOf(calculatePulseCost(UsageTotals(), config))
    }.collectAsStateWithLifecycle(initialValue = calculatePulseCost(UsageTotals(), config))
    val next = runtimeState.nextHeartbeatAt.takeIf { it > 0 }
        ?: (config.lastHeartbeatAt.takeIf { it > 0 } ?: now) + config.heartbeatIntervalMinutes.coerceIn(15, 240) * 60_000L
    val text = when {
        !config.enableHeartbeat -> stringResource(R.string.pulse_status_off)
        !pulseWithinHours(now, config.activeHoursStart, config.activeHoursEnd) -> stringResource(R.string.pulse_status_paused,
            "%02d:00".format(config.activeHoursStart), "%02d:00".format(config.activeHoursEnd))
        config.heartbeatLog.firstOrNull()?.result == "FAILED" -> stringResource(R.string.pulse_status_failed, pulseTime(next))
        else -> stringResource(R.string.pulse_status_running, pulseTime(next))
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
        val tokenText = stringResource(R.string.pulse_usage_tokens, compactPulseNumber(usage.totalTokens))
        val usageText = if (usage.costYuan == null) tokenText else stringResource(
            R.string.pulse_usage_estimate,
            tokenText,
            "%.4f".format(java.util.Locale.getDefault(), usage.costYuan),
        )
        Text(usageText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
