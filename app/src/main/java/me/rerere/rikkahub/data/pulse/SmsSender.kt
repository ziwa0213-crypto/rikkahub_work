package me.rerere.rikkahub.data.pulse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun validateSms(to: String, text: String): String {
    val number = normalizeSmsAddress(to)
    require(number.matches(Regex("^\\+?[0-9]{5,20}$"))) { "Invalid phone number." }
    require(text.isNotBlank() && text.length <= 1000) { "Message must be 1-1000 characters." }
    return number
}

internal suspend fun sendSms(context: Context, to: String, text: String): Int = withContext(Dispatchers.IO) {
    val number = validateSms(to, text)
    check(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        "SEND_SMS permission is not granted."
    }
    val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
    checkNotNull(manager) { "SMS service is unavailable." }
    try {
        val parts = manager.divideMessage(text)
        if (parts.size == 1) manager.sendTextMessage(number, null, text, null, null)
        else manager.sendMultipartTextMessage(number, null, parts, null, null)
        parts.size
    } catch (_: Exception) {
        // Platform errors can include the destination or text. Do not copy them into logs/history.
        error("SMS submission failed. Check permissions and the default SIM; delivery is not confirmed.")
    }
}
