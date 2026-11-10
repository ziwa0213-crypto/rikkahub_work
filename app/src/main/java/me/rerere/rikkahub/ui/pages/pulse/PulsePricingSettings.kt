package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.pulse.*
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal fun LazyListScope.pulsePricingItems(
    config: PulseConfig,
    usage: PulseCostSummary,
    update: ((PulseConfig) -> PulseConfig) -> Unit,
) {
    item {
        CardGroup {
            switchItem(
                checked = config.peakPricingEnabled,
                onCheckedChange = { enabled -> update { it.copy(peakPricingEnabled = enabled) } },
                headlineContent = { Text(stringResource(R.string.pulse_peak_pricing)) },
                supportingContent = { Text(stringResource(R.string.pulse_peak_pricing_desc)) },
            )
        }
    }
    item { Text(pulseUsageLine(usage.totalTokens, usage.costYuan, config.peakPricingEnabled), style = MaterialTheme.typography.titleSmall) }
    if (config.peakPricingEnabled) {
        item { Text(stringResource(R.string.pulse_price_recent_200), style = MaterialTheme.typography.bodySmall) }
        item { PulsePriceSegmentList(config, update) }
    } else {
        item {
            CardGroup(title = { Text(stringResource(R.string.pulse_cost_prices)) }) {
                formItem(headlineContent = { Text(stringResource(R.string.pulse_price_input)) }) {
                    PulsePriceInput(config.priceInputPerMillion) { value -> update { it.copy(priceInputPerMillion = value) } }
                }
                formItem(headlineContent = { Text(stringResource(R.string.pulse_price_output)) }) {
                    PulsePriceInput(config.priceOutputPerMillion) { value -> update { it.copy(priceOutputPerMillion = value) } }
                }
                formItem(headlineContent = { Text(stringResource(R.string.pulse_price_cached)) }) {
                    PulsePriceInput(config.priceCachedPerMillion) { value -> update { it.copy(priceCachedPerMillion = value) } }
                }
                formItem(headlineContent = { Text(stringResource(R.string.pulse_price_multiplier)) }) {
                    PulsePriceInput(config.priceMultiplier) { value -> update { it.copy(priceMultiplier = value) } }
                }
            }
        }
    }
}

@Composable
private fun PulsePriceSegmentList(config: PulseConfig, update: ((PulseConfig) -> PulseConfig) -> Unit) {
    var editing by remember { mutableStateOf<PulsePriceSegment?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pulse_price_segment), style = MaterialTheme.typography.titleMedium)
        if (config.priceSegments.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pulse_price_segment_empty))
                    Text(stringResource(R.string.pulse_price_segment_empty_desc), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        config.priceSegments.forEach { segment ->
            key(segment.id) {
                val colors = MaterialTheme.colorScheme
                val peak = segment.kind == PulsePriceKind.PEAK
                Card(onClick = { editing = segment }, colors = CardDefaults.cardColors(
                    containerColor = if (peak) colors.primaryContainer else colors.tertiaryContainer,
                    contentColor = if (peak) colors.onPrimaryContainer else colors.onTertiaryContainer,
                )) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(if (peak) colors.primary else colors.tertiary))
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(priceSegmentLabel(segment), style = MaterialTheme.typography.titleSmall)
                            Text(listOf(
                                stringResource(R.string.pulse_price_input) + " " + priceText(segment.inputPrice),
                                stringResource(R.string.pulse_price_output) + " " + priceText(segment.outputPrice),
                                stringResource(R.string.pulse_price_cached) + " " + priceText(segment.cachedPrice),
                                "×" + priceText(segment.multiplier),
                            ).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        val uncovered = remember(config.priceSegments) { uncoveredPulseQuarterHours(config.priceSegments) * 0.25 }
        if (config.priceSegments.isNotEmpty() && uncovered > 0) {
            Text(stringResource(R.string.pulse_price_uncovered, priceText(uncovered)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = { editing = PulsePriceSegment() }) { Text("+ " + stringResource(R.string.pulse_price_add_segment)) }
    }
    editing?.let { segment ->
        PulsePriceSegmentEditor(
            segment = segment,
            segments = config.priceSegments,
            onDismiss = { editing = null },
            onSave = { saved ->
                update { current ->
                    if (pulsePriceSegmentConflict(saved, current.priceSegments) != null) current else current.copy(
                        priceSegments = if (current.priceSegments.any { it.id == saved.id }) {
                            current.priceSegments.map { if (it.id == saved.id) saved else it }
                        } else current.priceSegments + saved,
                    )
                }
                editing = null
            },
            onDelete = if (config.priceSegments.any { it.id == segment.id }) ({
                update { it.copy(priceSegments = it.priceSegments.filterNot { item -> item.id == segment.id }) }
                editing = null
            }) else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PulsePriceSegmentEditor(
    segment: PulsePriceSegment,
    segments: List<PulsePriceSegment>,
    onDismiss: () -> Unit,
    onSave: (PulsePriceSegment) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var kind by remember { mutableStateOf(segment.kind) }
    var start by remember { mutableStateOf(priceTime(segment.startMinute)) }
    var end by remember { mutableStateOf(priceTime(segment.endMinute)) }
    var days by remember { mutableStateOf(segment.days) }
    var input by remember { mutableStateOf(priceText(segment.inputPrice)) }
    var output by remember { mutableStateOf(priceText(segment.outputPrice)) }
    var cached by remember { mutableStateOf(priceText(segment.cachedPrice)) }
    var multiplier by remember { mutableStateOf(priceText(segment.multiplier)) }
    var daysMenu by remember { mutableStateOf(false) }
    val startMinute = parsePulsePriceTime(start)
    val endMinute = parsePulsePriceTime(end)
    val prices = listOf(input, output, cached, multiplier).map { it.toDoubleOrNull()?.takeIf { v -> v.isFinite() && v in 0.0..1_000_000.0 } }
    val valid = startMinute != null && endMinute != null && prices.all { it != null }
    val candidate = if (valid) segment.copy(kind = kind, startMinute = startMinute!!, endMinute = endMinute!!,
        days = days, inputPrice = prices[0]!!, outputPrice = prices[1]!!, cachedPrice = prices[2]!!, multiplier = prices[3]!!) else null
    val conflict = candidate?.let { pulsePriceSegmentConflict(it, segments) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pulse_price_segment), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PulsePriceKind.entries.forEach { value ->
                    FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(priceKind(value)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.pulse_price_start_time)) }, isError = startMinute == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                OutlinedTextField(end, { end = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.pulse_price_end_time)) }, isError = endMinute == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            }
            Box {
                OutlinedButton(onClick = { daysMenu = true }) { Text(stringResource(R.string.pulse_price_days) + ": " + priceDays(days)) }
                DropdownMenu(expanded = daysMenu, onDismissRequest = { daysMenu = false }) {
                    listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7), emptyList(), listOf(1, 2, 3, 4, 5, 6)).forEach { value ->
                        DropdownMenuItem(text = { Text(priceDays(value)) }, onClick = { days = value; daysMenu = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentPriceField(input, { input = it }, R.string.pulse_price_input, Modifier.weight(1f))
                SegmentPriceField(output, { output = it }, R.string.pulse_price_output, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentPriceField(cached, { cached = it }, R.string.pulse_price_cached, Modifier.weight(1f))
                SegmentPriceField(multiplier, { multiplier = it }, R.string.pulse_price_multiplier, Modifier.weight(1f))
            }
            if (conflict != null) Text(stringResource(R.string.pulse_price_overlap, priceSegmentLabel(conflict)), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.pulse_price_delete), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_page_cancel)) }
                Button(onClick = { candidate?.let(onSave) }, enabled = valid && conflict == null) { Text(stringResource(R.string.pulse_price_save)) }
            }
        }
    }
}

@Composable
private fun SegmentPriceField(value: String, onChange: (String) -> Unit, label: Int, modifier: Modifier) {
    val number = value.toDoubleOrNull()
    OutlinedTextField(value, onChange, modifier, singleLine = true, label = { Text(stringResource(label)) },
        isError = number == null || !number.isFinite() || number !in 0.0..1_000_000.0,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun priceKind(kind: PulsePriceKind): String = stringResource(if (kind == PulsePriceKind.PEAK) R.string.pulse_price_kind_peak else R.string.pulse_price_kind_off)

@Composable
private fun priceDays(days: List<Int>): String = when (days.toSet()) {
    emptySet<Int>(), (1..7).toSet() -> stringResource(R.string.pulse_price_every_day)
    (1..5).toSet() -> stringResource(R.string.pulse_price_weekdays)
    setOf(6, 7) -> stringResource(R.string.pulse_price_weekend)
    (1..6).toSet() -> stringResource(R.string.pulse_price_six_days)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        days.filter { it in 1..7 }.joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
    }
}

@Composable
private fun priceSegmentLabel(segment: PulsePriceSegment): String = priceKind(segment.kind) + " " +
    priceTime(segment.startMinute) + "–" + priceTime(segment.endMinute) + " · " + priceDays(segment.days)

internal fun parsePulsePriceTime(value: String): Int? {
    if (!value.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) return null
    val hour = value.take(2).toInt()
    return if (hour < 24) hour * 60 + value.takeLast(2).toInt() else null
}

private fun priceTime(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)
private fun priceText(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
