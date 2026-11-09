package me.rerere.rikkahub.ui.pages.pulse

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.pulse.launchBatterySettings
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun PulseBackgroundSettingsPage() {
    val context = LocalContext.current
    val store = koinInject<SettingsStore>()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val battery = remember(revision) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val exact = remember(revision) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notification = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    fun open(action: String, appData: Boolean = true) {
        val intent = Intent(action).apply {
            if (appData) data = Uri.parse("package:${context.packageName}")
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        }
    }
    fun setEnabled(value: Boolean) { scope.launch { store.update { it.copy(pulse = it.pulse.copy(keepAliveEnabled = value)) } } }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.pulse_background_title)) }, navigationIcon = { BackButton() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp) + padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { CardGroup {
                switchItem(checked = settings.pulse.keepAliveEnabled, onCheckedChange = { if (it) confirm = true else setEnabled(false) },
                    headlineContent = { Text(stringResource(R.string.pulse_keepalive_title)) },
                    supportingContent = { Text(stringResource(R.string.pulse_keepalive_description)) })
            } }
            item { Text(stringResource(R.string.pulse_permissions_description)) }
            item { CardGroup {
                item(onClick = { launchBatterySettings(context) },
                    headlineContent = { Text(stringResource(R.string.pulse_permission_battery)) },
                    supportingContent = { PermissionState(battery) })
                item(onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                    headlineContent = { Text(stringResource(R.string.pulse_permission_autostart)) },
                    supportingContent = { Text(stringResource(R.string.pulse_permission_vendor)) })
                item(onClick = { if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
                    headlineContent = { Text(stringResource(R.string.pulse_permission_alarm)) },
                    supportingContent = { PermissionState(exact) })
                item(onClick = { open(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false) },
                    headlineContent = { Text(stringResource(R.string.pulse_permission_notification)) },
                    supportingContent = { PermissionState(notification) })
                item(onClick = { open(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false) },
                    headlineContent = { Text(stringResource(R.string.pulse_permission_fgs_notification)) },
                    supportingContent = { Text(stringResource(if (notification) R.string.pulse_permission_fgs_visible else R.string.pulse_permission_fgs_hidden)) })
            } }
            if (!battery || !exact || !notification) item { Text(stringResource(R.string.pulse_permissions_warning), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.pulse_keepalive_title)) }, text = { Text(stringResource(R.string.pulse_keepalive_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; setEnabled(true) }) { Text(stringResource(R.string.pulse_enable)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.chat_page_cancel)) } })
}

@Composable
private fun PermissionState(granted: Boolean) {
    Text(stringResource(if (granted) R.string.pulse_permission_granted else R.string.pulse_permission_missing))
}
