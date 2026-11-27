package me.rerere.rikkahub.data.ai.tools.local

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal data class AlarmRequest(val hour: Int? = null, val minute: Int? = null, val durationSeconds: Int? = null, val label: String? = null)

/** Validate before touching Android; a timer deliberately takes precedence over clock time. */
internal fun parseAlarmRequest(input: JsonElement): AlarmRequest {
    val args = input as? JsonObject ?: throw IllegalArgumentException("Alarm parameters must be an object.")
    fun integer(key: String): Int? {
        val value = args[key]?.takeUnless { it == JsonNull } ?: return null
        val number = value as? JsonPrimitive
        require(number != null && !number.isString) { "$key must be an integer." }
        return requireNotNull(number.intOrNull) { "$key must be an integer." }
    }
    val label = args["label"]?.takeUnless { it == JsonNull }?.let {
        require(it is JsonPrimitive && it.isString) { "label must be a string." }
        it.content
    }
    val duration = integer("duration_seconds")
    if (duration != null) {
        require(duration in 1..86_400) { "duration_seconds must be between 1 and 86400 (24 hours)." }
        return AlarmRequest(durationSeconds = duration, label = label)
    }
    val hour = integer("hour")
    val minute = integer("minute")
    require(hour != null && minute != null) { "Provide hour and minute, or duration_seconds; no time was assumed." }
    require(hour in 0..23) { "hour must be between 0 and 23." }
    require(minute in 0..59) { "minute must be between 0 and 59." }
    return AlarmRequest(hour = hour, minute = minute, label = label)
}

/** AlarmClock has no receipt. A changed next-alarm time is evidence, not a complete Clock database. */
internal suspend fun dispatchAlarmRequest(
    request: AlarmRequest,
    backgroundRestricted: Boolean = false,
    backgroundLaunch: Boolean = backgroundRestricted,
    readNextAlarm: () -> Long? = { null },
    launch: (AlarmRequest) -> Unit,
): JsonObject {
    fun nextAlarm(): Result<Long?> = try {
        Result.success(readNextAlarm())
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Result.failure(error)
    }

    val before = if (request.durationSeconds == null) nextAlarm() else Result.success(null)
    try {
        launch(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        return if (backgroundLaunch && error is SecurityException) alarmBackgroundBlocked()
        else alarmFailure("Clock setup request failed (${error.javaClass.simpleName}). Check system permissions and that a Clock app is available. 设置请求失败，请检查系统权限和系统时钟应用。")
    }

    // Timers are not exposed by getNextAlarmClock. An unchanged earlier alarm is inconclusive too.
    val verified = if (request.durationSeconds == null) {
        delay(600)
        val afterSnapshot = nextAlarm()
        val after = afterSnapshot.getOrNull()
        before.isSuccess && afterSnapshot.isSuccess && after != null && after != before.getOrNull() &&
            Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()).let {
                it.hour == request.hour && it.minute == request.minute
            }
    } else false
    if (backgroundRestricted && !verified) return alarmBackgroundBlocked()

    return buildJsonObject {
        put("success", true)
        put("status", "request_sent")
        put("type", if (request.durationSeconds != null) "timer" else "alarm")
        if (request.durationSeconds != null) put("duration_seconds", request.durationSeconds)
        else {
            put("hour", requireNotNull(request.hour))
            put("minute", requireNotNull(request.minute))
        }
        put("verified", verified)
        put("message", if (verified) {
            "Clock setup request sent. Check the system Clock app; it may require confirmation. 设置请求已发送，请在系统时钟中核实；系统可能要求确认。"
        } else {
            "Clock setup request sent, but saving could not be confirmed. Check the system Clock app; it may require confirmation or background launch permission. 设置请求已发送，但无法确认是否保存，请在系统时钟中核实；系统可能要求确认或允许后台弹出界面。"
        })
    }
}

private fun alarmBackgroundBlocked(): JsonObject = alarmFailure(
    message = "Background Clock setup could not be confirmed without background launch permission. Allow Display over other apps and, on some devices, Background pop-ups in system settings, then retry. 后台设置请求无法确认，缺少后台启动权限。请在系统设置中允许「显示在其他应用上层」；部分系统还需允许「后台弹出界面」，然后重试。",
    errorCode = "background_blocked",
)

internal fun alarmFailure(message: String, errorCode: String? = null): JsonObject = buildJsonObject {
    put("success", false)
    put("error", message)
    if (errorCode != null) {
        put("error_code", errorCode)
        put("verified", false)
    }
}
