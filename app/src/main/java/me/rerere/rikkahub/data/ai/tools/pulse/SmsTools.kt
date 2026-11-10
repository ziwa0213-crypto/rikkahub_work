package me.rerere.rikkahub.data.ai.tools.pulse

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.pulse.SmsReader
import me.rerere.rikkahub.data.pulse.sendSms
import java.time.Instant
import java.time.ZoneId

internal fun readSmsTool(context: Context, store: SettingsStore) = Tool(
    name = "read_sms",
    description = "Read recent inbox SMS (1-20, default 10), optionally only unread. Verification-code messages are redacted and personal numbers masked unless explicitly whitelisted. Results become part of conversation history. Does not mark messages read.",
    parameters = { InputSchema.Obj(buildJsonObject {
        put("limit", buildJsonObject { put("type", "integer"); put("description", "1-20, default 10") })
        put("unread_only", buildJsonObject { put("type", "boolean"); put("description", "Only system-unread messages; default false") })
    }) },
    execute = { input ->
        val config = store.awaitLoaded().pulse
        if (!config.enableSmsReading) pulseJsonResult(buildJsonObject { put("granted", SmsReader.hasPermission(context)); put("enabled", false) })
        else if (!SmsReader.hasPermission(context)) sensingPermissionDenied()
        else try {
            val args = input as? JsonObject
            val entries = SmsReader.read(context, (args?.get("limit") as? JsonPrimitive)?.intOrNull ?: 10,
                (args?.get("unread_only") as? JsonPrimitive)?.booleanOrNull ?: false, config.smsSenderWhitelist)
            // Recheck after IO so a revoked switch/filter cannot return data from an older configuration.
            val latest = store.settingsFlow.value.pulse
            if (!latest.enableSmsReading || latest.smsSenderWhitelist != config.smsSenderWhitelist)
                pulseJsonResult(buildJsonObject { put("enabled", false); put("hint", "SMS settings changed; retry the read.") })
            else if (!SmsReader.hasPermission(context)) sensingPermissionDenied()
            else pulseJsonResult(buildJsonObject {
                put("granted", true)
                putJsonArray("items") { entries.forEach { entry -> add(buildJsonObject {
                    put("from", entry.address)
                    put("at", Instant.ofEpochMilli(entry.date).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
                    entry.body?.let { put("text", it) }
                    entry.redactedKind?.let { put("kind", it) }
                    if (entry.truncated) put("truncated", true)
                }) } }
                put("note", "Messages matching the verification-code rules never return their body.")
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { sensingPermissionDenied() }
        catch (_: Exception) { pulseJsonResult(buildJsonObject { put("error", "SMS inbox is unavailable. Check system permissions.") }) }
    },
)

internal fun sendSmsTool(context: Context, store: SettingsStore) = Tool(
    name = "send_sms",
    description = "Submit an SMS using the default SIM. Requires explicit user approval every time, SEND_SMS permission and the sending switch. Submission is not a delivery confirmation. Maximum 1000 characters.",
    parameters = { InputSchema.Obj(buildJsonObject {
        put("to", buildJsonObject { put("type", "string"); put("description", "Destination phone number") })
        put("text", buildJsonObject { put("type", "string"); put("description", "Complete message, 1-1000 characters") })
    }, required = listOf("to", "text")) },
    needsApproval = { true },
    execute = { input ->
        check(store.awaitLoaded().pulse.enableSmsSending) { "SMS sending is disabled." }
        val args = input as? JsonObject
        val to = (args?.get("to") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val text = (args?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val parts = sendSms(context, to, text)
        pulseJsonResult(buildJsonObject { put("sent", true); put("parts", parts); put("delivery_confirmed", false) })
    },
)
