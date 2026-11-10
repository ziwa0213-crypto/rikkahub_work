package me.rerere.rikkahub.data.model

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class PulseConfigTest {
    @Test fun `legacy preferences default to disabled background automation`() {
        val pulse = emptyPreferences().toSettings().pulse
        assertFalse(pulse.keepAliveEnabled)
        assertFalse(pulse.enableHeartbeat)
        assertFalse(pulse.enableScheduledTasks)
        assertFalse(pulse.enableAlarm)
        assertFalse(pulse.autoConsolidateMemory)
        assertFalse(JsonInstant.decodeFromString<PulseConfig>("{}").autoConsolidateMemory)
        assertFalse(JsonInstant.decodeFromString<PulseConfig>("{}").enableAlarm)
        assertNull(pulse.conversationId)
    }
    @Test fun `pulse always reads live settings and never snapshots conversation config`() {
        val id = Uuid.random()
        val stale = Uuid.random()
        val conversation = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList(),
            config = ConversationConfig(chatModelId = stale))
        val settings = Settings(pulse = PulseConfig(chatModelId = id, reasoningLevel = ReasoningLevel.HIGH))
        assertSame(conversation, conversation.bindConfig(settings))
        assertEquals(id, settings.getAssistantOf(conversation).chatModelId)
        assertEquals(ReasoningLevel.HIGH, settings.getAssistantOf(conversation).reasoningLevel)
    }
    @Test fun `pulse memory cannot alias global or group memory`() {
        val pulse = PulseConfig().toAssistant(Settings()).copy(useGlobalMemory = true, memoryGroupId = Uuid.random())
        assertEquals("__pulse__", pulse.memoryId)
        assertNotEquals(Assistant().memoryId, pulse.memoryId)
        assertEquals(ToolApprovalMode.AllowAll, pulse.toolApprovalMode)
    }
    @Test fun `empty model follows global without changing global assistant`() {
        val settings = Settings()
        val pulse = settings.pulse.toAssistant(settings)
        assertEquals(settings.chatModelId, pulse.chatModelId)
        assertNotEquals(settings.assistantId, pulse.id)
    }
    @Test fun `all pulse settings survive preferences and JSON backup`() {
        val config = PulseConfig(chatModelId = Uuid.random(), systemPrompt = "custom", reasoningLevel = ReasoningLevel.HIGH,
            contextMessageLimit = 10, enableMemory = false, autoConsolidateMemory = true,
            conversationId = Uuid.random(), enableWebSearch = true,
            enableRecentChats = false, enabledSkills = listOf("future"), localTools = listOf("future"),
            quickMessageIds = setOf(Uuid.random()), enableInformation = false, enableCalendarCreate = false, enableAlarm = true, enableFileWrite = true, enableShell = true,
            enableNotificationReading = true, notificationWhitelist = listOf("chat"),
            notificationRetentionHours = 12, notificationBufferLimit = 50,
            enableSmsReading = true, enableSmsSending = true, smsPollEnabled = true,
            smsPollMinutes = 30, smsWatermarkId = 123L, smsSenderWhitelist = listOf("95588"),
            enableMcp = true, enableHeartbeat = true, heartbeatIntervalMinutes = 15, heartbeatPrompt = "check",
            activeHoursStart = 22, activeHoursEnd = 6, heartbeatSilentWhenNoContent = false,
            enableScheduledTasks = true, keepAliveEnabled = true, lastHeartbeatAt = 120,
            heartbeatLog = listOf(HeartbeatLogEntry(100, "OK")),
            tasks = listOf(PulseTask(id = "task", title = "test", createdAt = 100)))
        val prefs = mutablePreferencesOf().apply { putSettings(Settings(pulse = config)) }
        assertEquals(config, prefs.toSettings().pulse)
        assertEquals(config, JsonInstant.decodeFromString<PulseConfig>(JsonInstant.encodeToString(config)))
    }

    @Test fun `pulse extension selection updates only pulse skill and quick message settings`() {
        val config = PulseConfig(
            systemPrompt = "preserved",
            enableShell = true,
            enabledSkills = listOf("old"),
            quickMessageIds = setOf(Uuid.random()),
        )
        val selectedQuickMessages = setOf(Uuid.random())
        val updated = config.withAssistantUpdate(
            config.toAssistant(Settings()).copy(
                enabledSkills = setOf("new"),
                quickMessageIds = selectedQuickMessages,
            ),
        )

        assertEquals(listOf("new"), updated.enabledSkills)
        assertEquals(selectedQuickMessages, updated.quickMessageIds)
        assertEquals("preserved", updated.systemPrompt)
        assertTrue(updated.enableShell)
    }

    @Test fun `alarm registration follows its independent switch`() {
        assertFalse(LocalToolOption.Alarm in PulseConfig().toAssistant(Settings()).localTools)
        val config = PulseConfig(enableInformation = false, enableCalendarCreate = false, enableAlarm = true)
        assertEquals(listOf(LocalToolOption.Alarm), config.toAssistant(Settings()).localTools)
    }

    @Test fun `pulse dreaming follows its own opt in and does not change ordinary assistants`() {
        val assistant = Assistant(autoConsolidateMemory = true)
        val settings = Settings(assistants = listOf(assistant))
        assertFalse(settings.pulse.toAssistant(settings).autoConsolidateMemory)
        val enabled = settings.copy(pulse = settings.pulse.copy(autoConsolidateMemory = true))
        assertTrue(enabled.pulse.toAssistant(enabled).autoConsolidateMemory)
        assertEquals(PULSE_MEMORY_ID, enabled.pulse.toAssistant(enabled).memoryId)
        assertSame(settings.assistants, enabled.assistants)
        assertEquals(settings.pulse.copy(autoConsolidateMemory = true), enabled.pulse)
    }
}
