package me.rerere.rikkahub.service.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import me.rerere.rikkahub.RouteActivity

const val ACTION_PULSE_BATTERY_SETTINGS = "me.rerere.rikkahub.action.PULSE_BATTERY_SETTINGS"

fun batteryKeepIntent(context: Context): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    .setData(Uri.parse("package:${context.packageName}"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Try the direct system dialog, then the exemption list, then this app's details page. */
fun launchBatterySettings(context: Context) {
    val candidates = listOf(
        batteryKeepIntent(context),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    candidates.firstOrNull { runCatching { context.startActivity(it) }.isSuccess }
}

fun batteryKeepRouteIntent(context: Context): Intent = Intent(context, RouteActivity::class.java).apply {
    action = ACTION_PULSE_BATTERY_SETTINGS
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}
