package me.rerere.rikkahub.data.pulse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SmsDiscovery(val maxScannedId: Long, val senders: List<String>, val senderCount: Int)

/** Count before masking: different phone numbers can have the same masked representation. */
internal fun smsDiscovery(maxScannedId: Long, addresses: List<String>, whitelist: List<String>): SmsDiscovery {
    val allowed = addresses.filter { smsSenderAllowed(it, whitelist) }
    return SmsDiscovery(maxScannedId, allowed.map { maskSmsAddress(it, whitelist) },
        allowed.map(::normalizeSmsAddress).distinct().size)
}

internal object SmsReader {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context, limit: Int, unreadOnly: Boolean, whitelist: List<String>): List<SmsEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SmsEntry>()
        checkNotNull(context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address", "body", "date"),
            if (unreadOnly) "read = ?" else null, if (unreadOnly) arrayOf("0") else null, "date DESC, _id DESC",
        )) { "SMS inbox is unavailable." }.use { cursor ->
            while (result.size < limit.coerceIn(1, 20) && cursor.moveToNext()) {
                redactedSmsEntry(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(),
                    cursor.getLong(3), whitelist)?.let(result::add)
            }
        }
        result
    }

    suspend fun currentMaxId(context: Context): Long = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"), null, null, "_id DESC")) {
            "SMS inbox is unavailable."
        }.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    /** Polling deliberately never queries the body column. Scan ascending so overflow is not lost. */
    suspend fun discover(context: Context, sinceId: Long, whitelist: List<String>): SmsDiscovery = withContext(Dispatchers.IO) {
        var scanned = sinceId
        val senders = mutableListOf<String>()
        checkNotNull(context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address"),
            "_id > ?", arrayOf(sinceId.toString()), "_id ASC")) { "SMS inbox is unavailable." }.use { cursor ->
            var count = 0
            while (count++ < 50 && cursor.moveToNext()) {
                scanned = maxOf(scanned, cursor.getLong(0))
                val address = cursor.getString(1).orEmpty()
                senders += address
            }
        }
        smsDiscovery(scanned, senders, whitelist)
    }
}
