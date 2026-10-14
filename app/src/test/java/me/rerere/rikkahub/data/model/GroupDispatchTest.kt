package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GroupDispatchTest {
    private val tag = Tag(Uuid.random(), "Code Edit")
    private val deputy = GroupMember(name = "Deputy")
    private val a = GroupMember(name = "Ann", tags = listOf(tag.id))
    private val b = GroupMember(name = "Ann Lee", tags = listOf(tag.id))
    private val off = GroupMember(name = "Disabled", tags = listOf(tag.id))
    private val settings = Settings(groupTags = listOf(tag), groupMembers = listOf(deputy, a, b, off))
    private val group = Group(enableDeputyDispatch = true, deputyMemberId = deputy.id, members = listOf(
        GroupMemberRef(deputy.id, priority = 0), GroupMemberRef(a.id, priority = 1, talkativeness = 0.2f),
        GroupMemberRef(b.id, priority = 2, talkativeness = 0.8f), GroupMemberRef(off.id, enabled = false)))

    @Test fun namesAndTagsSelectEnabledMembersByWeightOnly() {
        assertEquals(listOf(b), group.resolveDispatch(settings, "@Ann Lee please").members)
        assertEquals(listOf(b, a), group.resolveDispatch(settings, "@Code Edit").members)
        assertEquals(listOf(b, a), group.resolveDispatch(settings, "@Ann @Ann Lee @Ann").members)
        assertEquals(listOf(a, b, deputy), group.enabledMembers(settings))
        assertEquals(listOf(deputy, a, b), group.enabledMembers(settings, deputyFirst = true))
    }

    @Test fun memberNameWinsOverTagAndInvalidMentionsDoNothing() {
        val settings = settings.copy(groupTags = listOf(tag.copy(name = a.name)))
        assertEquals(listOf(a), group.resolveDispatch(settings, "@Ann").members)
        listOf("@Deputy", "@Disabled", "@Unknown", "a@Ann.com", "@Ann_foo").forEach {
            assertFalse(group.resolveDispatch(settings, it).matched)
        }
        assertFalse(group.copy(enableDeputyDispatch = false).resolveDispatch(settings, "@Ann").matched)
        assertFalse("mail@Code Edit".containsMention("Code Edit"))
    }

    @Test fun oldDataDefaultsAndNewFieldsRoundTrip() {
        val legacy = JsonInstant.decodeFromString<Group>("{}")
        assertFalse(legacy.enableDeputyDispatch)
        assertTrue(legacy.enableAutoCompress)
        assertEquals(0.7f, legacy.compressThresholdRatio)
        assertEquals(40, legacy.compressMessageCount)
        assertEquals(group, JsonInstant.decodeFromString<Group>(JsonInstant.encodeToString(group)))
        val current = settings.copy(groupCompressPrompt = "{primary} {others}")
        assertEquals(current.groupCompressPrompt, JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(current)).groupCompressPrompt)
    }

    @Test fun interruptedMultiMessageDispatchIsNeverRestoredAsSummary() {
        val partialDispatch = listOf(UIMessage.assistant("@Ann please check").withGroupSender(deputy, "Deputy"),
            UIMessage.assistant("Checking with a tool first").withGroupSender(deputy, "Deputy"))
        assertEquals("", partialDispatch.completedDispatchText(deputy.id, deputy.id.toString()))
        assertTrue(partialDispatch.completedDispatchText(deputy.id, a.id.toString()).contains("@Ann"))
        val summaryHistory = partialDispatch + UIMessage.assistant("Member answer").withGroupSender(a, "Ann") +
            UIMessage.assistant("Summary must not assign @Ann Lee").withGroupSender(deputy, "Deputy")
        val restored = summaryHistory.completedDispatchText(deputy.id, deputy.id.toString())
        assertTrue(restored.contains("@Ann please check"))
        assertFalse(restored.contains("@Ann Lee"))
        assertEquals(listOf(a), group.resolveDispatch(settings, restored).members)
    }
}
