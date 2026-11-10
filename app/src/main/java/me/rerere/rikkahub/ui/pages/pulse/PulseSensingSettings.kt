package me.rerere.rikkahub.ui.pages.pulse

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.service.pulse.hasNotificationAccess
import me.rerere.rikkahub.service.pulse.openNotificationAccessSettings
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem

@Composable
internal fun PulseSensingSettings(config: PulseConfig, update: ((PulseConfig) -> PulseConfig) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var notifications by remember { mutableStateOf(hasNotificationAccess(context)) }
    var read by remember { mutableStateOf(granted(Manifest.permission.READ_SMS)) }
    var send by remember { mutableStateOf(granted(Manifest.permission.SEND_SMS)) }
    var picker by remember { mutableStateOf(false) }
    fun openDetails() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null))) }
    }
    fun denied(permission: String) {
        if (context.componentActivity()?.shouldShowRequestPermissionRationale(permission) == false) openDetails()
    }
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        read = it
        if (!it) denied(Manifest.permission.READ_SMS)
    }
    val sendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        send = it
        if (!it) denied(Manifest.permission.SEND_SMS)
    }
    LifecycleResumeEffect(Unit) {
        notifications = hasNotificationAccess(context)
        read = granted(Manifest.permission.READ_SMS)
        send = granted(Manifest.permission.SEND_SMS)
        onPauseOrDispose { }
    }
    fun notificationAccess() {
        if (!openNotificationAccessSettings(context)) Toast.makeText(context,
            resources.getString(R.string.pulse_sensing_settings_unavailable), Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardGroup(title = { Text(stringResource(R.string.pulse_notify_title)) }) {
            switchItem(config.enableNotificationReading, { value ->
                update { it.copy(enableNotificationReading = value) }
                if (value && !hasNotificationAccess(context)) notificationAccess()
            }, headlineContent = { Text(stringResource(R.string.pulse_notify_title)) },
                supportingContent = { Text(stringResource(R.string.pulse_notify_desc)) })
            if (config.enableNotificationReading) {
                formItem { SensingPermissionRow(notifications, R.string.pulse_notify_not_granted, ::notificationAccess) }
                item(onClick = { picker = true }, headlineContent = { Text(stringResource(R.string.pulse_notify_whitelist)) },
                    supportingContent = { Text(if (config.notificationWhitelist.isEmpty()) stringResource(R.string.pulse_notify_whitelist_empty)
                        else stringResource(R.string.pulse_notify_selected_count, config.notificationWhitelist.size)) })
                formItem { SensingNumberInput(config.notificationRetentionHours, 1..72, R.string.pulse_notify_retention) { value ->
                    update { it.copy(notificationRetentionHours = value) }
                } }
            }
            item(headlineContent = { Text(stringResource(R.string.pulse_notify_privacy), style = MaterialTheme.typography.bodySmall) })
        }
        CardGroup(title = { Text(stringResource(R.string.pulse_sms_read_title)) }) {
            switchItem(config.enableSmsReading, { value ->
                update { it.copy(enableSmsReading = value) }
                if (value && !granted(Manifest.permission.READ_SMS)) readLauncher.launch(Manifest.permission.READ_SMS)
            }, headlineContent = { Text(stringResource(R.string.pulse_sms_read_title)) },
                supportingContent = { Text(stringResource(R.string.pulse_sms_read_desc)) })
            if (config.enableSmsReading) {
                formItem { SensingPermissionRow(read, R.string.pulse_sms_permission_denied) { readLauncher.launch(Manifest.permission.READ_SMS) } }
                switchItem(config.smsPollEnabled, { value -> update { it.copy(smsPollEnabled = value) } },
                    headlineContent = { Text(stringResource(R.string.pulse_sms_poll_title)) },
                    supportingContent = { Text(stringResource(R.string.pulse_sms_poll_desc)) })
                if (config.smsPollEnabled) formItem { SensingNumberInput(config.smsPollMinutes, 5..240, R.string.pulse_sms_poll_interval) { value ->
                    update { it.copy(smsPollMinutes = value) }
                } }
                formItem { SmsSenderFilter(config.smsSenderWhitelist) { values -> update { it.copy(smsSenderWhitelist = values) } } }
            }
        }
        CardGroup(title = { Text(stringResource(R.string.pulse_sms_send_title)) }) {
            switchItem(config.enableSmsSending, { value ->
                update { it.copy(enableSmsSending = value) }
                if (value && !granted(Manifest.permission.SEND_SMS)) sendLauncher.launch(Manifest.permission.SEND_SMS)
            }, headlineContent = { Text(stringResource(R.string.pulse_sms_send_title)) },
                supportingContent = { Text(stringResource(R.string.pulse_sms_send_desc)) })
            if (config.enableSmsSending) formItem { SensingPermissionRow(send, R.string.pulse_sms_permission_denied) { sendLauncher.launch(Manifest.permission.SEND_SMS) } }
            item(headlineContent = { Text(stringResource(R.string.pulse_sms_privacy), style = MaterialTheme.typography.bodySmall) })
        }
    }
    if (picker) NotificationAppsPicker(config.notificationWhitelist, update, onDismiss = { picker = false })
}

@Composable
private fun SensingPermissionRow(granted: Boolean, missing: Int, request: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (granted) R.string.pulse_notify_granted else missing), modifier = Modifier.weight(1f),
            color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        if (!granted) TextButton(onClick = request) { Text(stringResource(R.string.pulse_notify_grant)) }
    }
}

@Composable
private fun SensingNumberInput(value: Int, range: IntRange, label: Int, commit: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value.toString() }
    val parsed = text.toIntOrNull()
    OutlinedTextField(text, { input ->
        if (input.length <= 3 && input.all(Char::isDigit)) {
            text = input
            input.toIntOrNull()?.takeIf { it in range }?.let(commit)
        }
    }, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, singleLine = true, label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(R.string.pulse_sensing_range, range.first, range.last)) },
        isError = parsed == null || parsed !in range, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun SmsSenderFilter(values: List<String>, commit: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(values.joinToString(", ")) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(values, focused) { if (!focused) text = values.joinToString(", ") }
    OutlinedTextField(text, { input ->
        text = input
        commit(input.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty).distinct())
    }, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, label = { Text(stringResource(R.string.pulse_sms_sender_filter)) },
        supportingText = { Text(stringResource(R.string.pulse_sms_sender_filter_hint)) })
}

private data class NotificationApp(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable?)

@Composable
private fun NotificationAppsPicker(selected: List<String>, update: ((PulseConfig) -> PulseConfig) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<NotificationApp>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val manager = context.packageManager
            runCatching {
                manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.applicationInfo }.filter { it.enabled && it.packageName != context.packageName }
                    .distinctBy { it.packageName }.map { info -> NotificationApp(info.packageName,
                        manager.getApplicationLabel(info).toString(), runCatching { manager.getApplicationIcon(info) }.getOrNull()) }
            }.getOrDefault(emptyList())
        }
    }
    val appScope = apps.orEmpty().map { it.packageName }
    fun selectAll() = update { pulse ->
        pulse.copy(notificationWhitelist = selectAllNotificationApps(pulse.notificationWhitelist, appScope))
    }
    fun invert() = update { pulse ->
        pulse.copy(notificationWhitelist = invertNotificationApps(pulse.notificationWhitelist, appScope))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pulse_notify_whitelist), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { selectAll() }, enabled = !apps.isNullOrEmpty()) {
                Text(stringResource(R.string.pulse_notify_select_all))
            }
            TextButton(onClick = { invert() }, enabled = !apps.isNullOrEmpty()) {
                Text(stringResource(R.string.pulse_notify_invert))
            }
        }
        if (apps == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (apps.orEmpty().isEmpty()) Text(stringResource(R.string.pulse_notify_apps_empty), modifier = Modifier.padding(16.dp))
        else LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(apps.orEmpty().sortedWith(compareBy<NotificationApp> { it.packageName !in selected }.thenBy { it.label.lowercase() }), key = { it.packageName }) { app ->
                fun toggle() = update { pulse -> pulse.copy(notificationWhitelist = if (app.packageName in pulse.notificationWhitelist)
                    pulse.notificationWhitelist - app.packageName else pulse.notificationWhitelist + app.packageName) }
                ListItem(modifier = Modifier.clickable { toggle() },
                    headlineContent = { Text(app.label) }, supportingContent = { Text(app.packageName) },
                    leadingContent = { AsyncImage(model = app.icon, contentDescription = null, modifier = Modifier.size(36.dp)) },
                    trailingContent = { Checkbox(app.packageName in selected, onCheckedChange = { toggle() }) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.pulse_notify_selected_of, selected.count { it in appScope }, appScope.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private tailrec fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.componentActivity()
    else -> null
}
