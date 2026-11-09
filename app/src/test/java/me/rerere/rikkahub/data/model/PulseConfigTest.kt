package me.rerere.rikkahub.data.model

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.repository.memoryId
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
            contextMessageLimit = 10, enableMemory = false, conversationId = Uuid.random(), enableWebSearch = true,
            enableRecentChats = false, enabledSkills = listOf("future"), localTools = listOf("future"),
            enableInformation = false, enableCalendarCreate = false, enableFileWrite = true, enableShell = true,
            enableMcp = true, enableHeartbeat = true, heartbeatIntervalMinutes = 15, heartbeatPrompt = "check",
            activeHoursStart = 22, activeHoursEnd = 6, heartbeatSilentWhenNoContent = false,
            enableScheduledTasks = true, keepAliveEnabled = true, lastHeartbeatAt = 120,
            heartbeatLog = listOf(HeartbeatLogEntry(100, "OK")),
            tasks = listOf(PulseTask(id = "task", title = "test", createdAt = 100)))
        val prefs = mutablePreferencesOf().apply { putSettings(Settings(pulse = config)) }
        assertEquals(config, prefs.toSettings().pulse)
        assertEquals(config, JsonInstant.decodeFromString<PulseConfig>(JsonInstant.encodeToString(config)))
    }
}
