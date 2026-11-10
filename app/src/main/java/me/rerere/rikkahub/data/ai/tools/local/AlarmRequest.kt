package me.rerere.rikkahub.data.ai.tools.local

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

/** Success means dispatch only: AlarmClock has no acknowledgement that an alarm was saved. */
internal fun dispatchAlarmRequest(request: AlarmRequest, foreground: Boolean, launch: (AlarmRequest) -> Unit): JsonObject {
    if (!foreground) return alarmFailure("Please set alarms while the app is in the foreground. 请在应用前台设置闹钟。")
    return try {
        launch(request)
        buildJsonObject {
            put("success", true)
            put("status", "request_sent")
            put("type", if (request.durationSeconds != null) "timer" else "alarm")
            if (request.durationSeconds != null) put("duration_seconds", request.durationSeconds)
            else {
                put("hour", requireNotNull(request.hour))
                put("minute", requireNotNull(request.minute))
            }
            put("message", "Clock setup request sent. Check the system Clock app; it may require confirmation. 设置请求已发送，请在系统时钟中核实；系统可能要求确认。")
        }
    } catch (error: RuntimeException) {
        alarmFailure("Clock setup request failed (${error.javaClass.simpleName}). Open the app in the foreground and check that a system Clock app is available. 设置请求失败，请在应用前台重试并检查系统时钟应用。")
    }
}

internal fun alarmFailure(message: String): JsonObject = buildJsonObject {
    put("success", false)
    put("error", message)
}
