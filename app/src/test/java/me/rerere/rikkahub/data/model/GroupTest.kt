package me.rerere.rikkahub.data.model

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.CustomBody
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.ui.pages.group.selectedGroup
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GroupTest {
    @Test
    fun legacyGroupsAndMembersUseDefaultsForNewOptions() {
        val group = JsonInstant.decodeFromString<Group>("""{"name":"legacy"}""")
        val member = JsonInstant.decodeFromString<GroupMember>("""{"name":"legacy"}""")
        assertTrue(group.localTools.isEmpty())
        assertTrue(group.mcpServers.isEmpty())
        assertTrue(group.customHeaders.isEmpty())
        assertTrue(group.customBodies.isEmpty())
        assertTrue(group.modeInjectionIds.isEmpty())
        assertTrue(group.lorebookIds.isEmpty())
        assertTrue(group.enabledSkills.isEmpty())
        assertNull(group.background)
        assertEquals(1.0f, group.backgroundOpacity)
        assertFalse(group.useGradientBackground)
        assertEquals(ReasoningLevel.AUTO, group.reasoningLevel)
        assertFalse(group.isPinned)
        assertNull(member.temperature)
        assertNull(member.topP)
        assertEquals(0, member.contextMessageLimit)
        assertNull(member.maxTokens)
    }

    @Test
    fun pinnedGroupsSurviveNormalizationAndPersistenceAlongsideReasoningAndResources() {
        val member = GroupMember()
        val group = Group(name = "Pinned", isPinned = true, reasoningLevel = ReasoningLevel.MAX,
            members = listOf(GroupMemberRef(member.id)), localTools = listOf(LocalToolOption.TimeInfo),
            background = "file:///private/pinned.png")
        val other = Group(name = "Other", reasoningLevel = ReasoningLevel.LOW)
        val settings = Settings(groups = listOf(other, group), groupMembers = listOf(member))
        val normalized = settings.normalizeGroupData()
        val listRestored = JsonInstant.decodeFromString<List<Group>>(JsonInstant.encodeToString(normalized.groups))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(normalized)).normalizeGroupData()
        assertEquals(listOf(other, group), listRestored)
        assertEquals(settings.groups, restored.groups)
        assertTrue(restored.selectedGroup(group.id)!!.isPinned)
        assertEquals(ReasoningLevel.MAX, restored.selectedGroup(group.id)!!.reasoningLevel)
        assertFalse(restored.selectedGroup(other.id)!!.isPinned)
        assertEquals(settings.assistants, restored.assistants)
        assertEquals(settings.groupMembers, restored.groupMembers)
    }

    @Test
    fun groupReasoningLevelsSurviveDatastoreListAndSettingsRoundTrips() {
        val groups = ReasoningLevel.entries.map { level -> Group(name = level.name, reasoningLevel = level) }
        val listPayload = JsonInstant.encodeToString(groups)
        assertTrue(listPayload.contains("\"reasoningLevel\":\"high\""))
        val restoredGroups = JsonInstant.decodeFromString<List<Group>>(listPayload)
        assertEquals(groups, restoredGroups)
        val settings = Settings(groups = restoredGroups)
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings)).normalizeGroupData()
        assertEquals(groups, restored.groups)
        assertEquals(ReasoningLevel.entries, restored.groups.map { it.reasoningLevel })
    }

    @Test
    fun updatingSelectedGroupReasoningKeepsOtherGroupsMembersAndAssistantsUnchanged() {
        val member = GroupMember(reasoningLevel = ReasoningLevel.LOW)
        val first = Group(name = "First", members = listOf(GroupMemberRef(member.id)))
        val second = Group(name = "Second", members = first.members)
        val currentAssistant = Assistant(name = "Current", reasoningLevel = ReasoningLevel.MEDIUM)
        val otherAssistant = Assistant(name = "Other", reasoningLevel = ReasoningLevel.OFF)
        val settings = Settings(
            groups = listOf(first, second), groupMembers = listOf(member),
            assistants = listOf(currentAssistant, otherAssistant), assistantId = currentAssistant.id,
        )
        val selected = settings.selectedGroup(first.id)!!
        val updated = settings.copy(groups = settings.groups.map {
            if (it.id == selected.id) it.copy(reasoningLevel = ReasoningLevel.HIGH) else it
        }).normalizeGroupData()
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(updated)).normalizeGroupData()
        assertEquals(ReasoningLevel.HIGH, restored.selectedGroup(first.id)!!.reasoningLevel)
        assertEquals(second, restored.selectedGroup(second.id))
        assertEquals(listOf(member), restored.groupMembers)
        assertEquals(settings.assistants, restored.assistants)
        assertEquals(settings.assistantId, restored.assistantId)
        assertEquals(ReasoningLevel.MEDIUM, restored.getCurrentAssistant().reasoningLevel)
        assertEquals(ReasoningLevel.AUTO, settings.selectedGroup(first.id)!!.reasoningLevel)
    }

    @Test
    fun importedMemberReasoningIsRetainedWithoutSettingGroupReasoning() {
        val assistant = Assistant(reasoningLevel = ReasoningLevel.MAX)
        val member = GroupMember().importSnapshot(assistant)
        val group = Group(members = listOf(GroupMemberRef(member.id)))
        val settings = Settings(groupMembers = listOf(member), groups = listOf(group))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings)).normalizeGroupData()
        assertEquals(ReasoningLevel.MAX, restored.groupMembers.single().reasoningLevel)
        assertEquals(ReasoningLevel.AUTO, restored.groups.single().reasoningLevel)
    }

    @Test
    fun groupResourcesBackgroundAndMemberTuningSurviveRoundTrip() {
        val member = GroupMember(temperature = 1.5f, topP = 0.8f, contextMessageLimit = 16, maxTokens = 1024)
        val group = Group(
            members = listOf(GroupMemberRef(member.id)), mcpServers = setOf(Uuid.random()),
            localTools = listOf(LocalToolOption.TimeInfo, LocalToolOption.Calendar),
            modeInjectionIds = setOf(Uuid.random()), lorebookIds = setOf(Uuid.random()),
            enabledSkills = setOf("skill"), customHeaders = listOf(CustomHeader("X-Test", "value")),
            customBodies = listOf(CustomBody("test", JsonPrimitive(true))),
            background = "file:///private/group.png", backgroundOpacity = 0.4f, useGradientBackground = true,
        )
        val settings = Settings(groupMembers = listOf(member), groups = listOf(group))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings)).normalizeGroupData()
        assertEquals(listOf(member), restored.groupMembers)
        assertEquals(listOf(group), restored.groups)
        assertEquals(settings.assistants, restored.assistants)
    }

    @Test
    fun importDoesNotCopyAssistantTuningOrOverwriteMemberTuning() {
        val assistant = Assistant(temperature = 1.9f, topP = 0.9f, contextMessageLimit = 42, maxTokens = 2048)
        val fresh = GroupMember().importSnapshot(assistant)
        assertNull(fresh.temperature)
        assertNull(fresh.topP)
        assertEquals(0, fresh.contextMessageLimit)
        assertNull(fresh.maxTokens)
        val tuned = GroupMember(temperature = 0.5f, topP = 0.2f, contextMessageLimit = 4, maxTokens = 256)
        val imported = tuned.importSnapshot(assistant)
        assertEquals(tuned.temperature, imported.temperature)
        assertEquals(tuned.topP, imported.topP)
        assertEquals(tuned.contextMessageLimit, imported.contextMessageLimit)
        assertEquals(tuned.maxTokens, imported.maxTokens)
    }

    @Test
    fun libraryOrderSurvivesNormalizationWithoutChangingSpeakingOrder() {
        val first = GroupMember(createdAt = 1)
        val second = GroupMember(createdAt = 2)
        val group = Group(members = listOf(GroupMemberRef(first.id), GroupMemberRef(second.id, priority = 1)))
        val otherGroup = Group(createdAt = 3)
        val settings = Settings(groupMembers = listOf(second, first), groups = listOf(otherGroup, group))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings)).normalizeGroupData()
        assertEquals(listOf(second, first), restored.groupMembers)
        assertEquals(listOf(otherGroup, group), restored.groups)
        assertEquals(listOf(first.id, second.id), restored.groups[1].members.map { it.memberId })
        assertEquals(otherGroup, restored.selectedGroup(null))
        assertEquals(otherGroup, restored.selectedGroup(Uuid.random()))
        assertEquals(group, restored.selectedGroup(group.id))
        assertNull(Settings(groups = emptyList()).selectedGroup(null))
    }

    @Test
    fun legacySettingsUseChatModeAndEmptyGroupLists() {
        val settings = JsonInstant.decodeFromString<Settings>("""{"developerMode":true}""")
        assertEquals(UiMode.CHAT, settings.uiMode)
        assertTrue(settings.groupMembers.isEmpty())
        assertTrue(settings.groups.isEmpty())
        assertTrue(settings.developerMode)
    }

    @Test
    fun settingsRoundTripPreservesModeGroupsAndExistingProviderCredentials() {
        val member = GroupMember(name = "Member", systemPrompt = "prompt", reasoningLevel = ReasoningLevel.HIGH)
        val group = Group(name = "Group", members = listOf(GroupMemberRef(member.id, enabled = false, talkativeness = 0.7f)))
        val settings = Settings(uiMode = UiMode.GROUP, groupMembers = listOf(member), groups = listOf(group),
            providers = listOf(ProviderSetting.OpenAI(apiKey = "local-test-key")), assistantId = Uuid.random())
        val decoded = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings))
        assertEquals(UiMode.GROUP, decoded.uiMode)
        assertEquals(settings.assistantId, decoded.assistantId)
        assertEquals(settings.groupMembers, decoded.groupMembers)
        assertEquals(settings.groups, decoded.groups)
        assertEquals("local-test-key", (decoded.providers.single() as ProviderSetting.OpenAI).apiKey)
    }

    @Test
    fun datastoreListPayloadsRoundTripWithDefaultMemberParameters() {
        val member = JsonInstant.decodeFromString<GroupMember>("{}")
        val refs = JsonInstant.decodeFromString<List<GroupMemberRef>>("""[{"memberId":"${member.id}"}]""")
        assertTrue(member.streamOutput)
        assertEquals(ReasoningLevel.AUTO, member.reasoningLevel)
        assertTrue(refs.single().enabled)
        assertEquals(0.5f, refs.single().talkativeness)
        val group = Group(members = refs)
        assertEquals(listOf(group), JsonInstant.decodeFromString<List<Group>>(JsonInstant.encodeToString(listOf(group))))
        assertEquals(listOf(member), JsonInstant.decodeFromString<List<GroupMember>>(JsonInstant.encodeToString(listOf(member))))
    }

    @Test
    fun assistantImportCopiesExactlyFiveFieldsIntoAnIndependentSnapshot() {
        val tagId = Uuid.random()
        val assistant = Assistant(name = "Source", chatModelId = Uuid.random(), systemPrompt = "Source prompt",
            streamOutput = false, reasoningLevel = ReasoningLevel.HIGH, tags = listOf(Uuid.random()),
            avatar = Avatar.Emoji("S"), enableMemory = true)
        val initial = GroupMember(tags = listOf(tagId), createdAt = 123L)
        val snapshot = initial.importSnapshot(assistant)
        val changed = assistant.copy(name = "Changed", systemPrompt = "New prompt", chatModelId = Uuid.random())
        assertEquals("Source", snapshot.name)
        assertEquals(assistant.chatModelId, snapshot.chatModelId)
        assertNotEquals(changed.chatModelId, snapshot.chatModelId)
        assertEquals(assistant.systemPrompt, snapshot.systemPrompt)
        assertFalse(snapshot.streamOutput)
        assertEquals(assistant.reasoningLevel, snapshot.reasoningLevel)
        assertEquals(initial.id, snapshot.id)
        assertNotEquals(assistant.id, snapshot.id)
        assertEquals(initial.tags, snapshot.tags)
        assertEquals(123L, snapshot.createdAt)
        assertEquals(Avatar.Dummy, snapshot.avatar)
        assertFalse(JsonInstant.encodeToString(snapshot).contains("assistantId"))
    }

    @Test
    fun normalizationFiltersStaleTagsAndMemberReferencesWithoutChangingExistingSettings() {
        val validTag = Tag(Uuid.random(), "Tag")
        val staleTag = Uuid.random()
        val member = GroupMember(tags = listOf(validTag.id, staleTag, validTag.id))
        val group = Group(tags = listOf(staleTag, validTag.id), members = listOf(
            GroupMemberRef(Uuid.random()), GroupMemberRef(member.id, priority = 8, enabled = false), GroupMemberRef(member.id)))
        val assistant = Assistant(name = "Keep", systemPrompt = "Keep prompt")
        val settings = Settings(assistantTags = listOf(validTag), groupTags = listOf(validTag), groupMembers = listOf(member, member),
            groups = listOf(group, group), assistants = listOf(assistant))
        val normalized = settings.normalizeGroupData()
        assertEquals(listOf(validTag.id), normalized.groupMembers.single().tags)
        assertEquals(listOf(validTag.id), normalized.groups.single().tags)
        assertEquals(member.id, normalized.groups.single().members.single().memberId)
        assertFalse(normalized.groups.single().members.single().enabled)
        assertEquals(0, normalized.groups.single().members.single().priority)
        assertEquals(settings.assistants, normalized.assistants)
        assertEquals(settings.providers, normalized.providers)
        assertEquals(settings.assistantId, normalized.assistantId)
        assertEquals(settings.displaySetting, normalized.displaySetting)
        assertEquals(normalized, normalized.normalizeGroupData())
    }

    @Test
    fun deletingMemberCleansEveryGroupAndKeepsOtherMembers() {
        val member = GroupMember()
        val other = GroupMember()
        val refs = listOf(GroupMemberRef(member.id), GroupMemberRef(other.id, priority = 1))
        val settings = Settings(groupMembers = listOf(member, other), groups = listOf(Group(members = refs), Group(members = refs)))
        val deleted = settings.copy(groupMembers = listOf(other)).normalizeGroupData()
        assertEquals(listOf(other), deleted.groupMembers)
        deleted.groups.forEach {
            assertEquals(listOf(GroupMemberRef(other.id)), it.members)
        }
        assertEquals(2, settings.groupMembers.size)
    }

    @Test
    fun deletingGroupKeepsMemberLibraryAndOtherGroups() {
        val member = GroupMember()
        val group = Group(members = listOf(GroupMemberRef(member.id)))
        val other = Group()
        val settings = Settings(groupMembers = listOf(member), groups = listOf(group, other))
        val deleted = settings.copy(groups = settings.groups.filterNot { it.id == group.id }).normalizeGroupData()
        assertEquals(listOf(member), deleted.groupMembers)
        assertEquals(listOf(other), deleted.groups)
    }

    @Test
    fun reorderedMembersRenumberPriorityWithoutResettingOtherParameters() {
        val first = GroupMemberRef(Uuid.random(), enabled = false, talkativeness = 0.7f)
        val second = GroupMemberRef(Uuid.random(), talkativeness = 0.3f)
        val reordered = Group().withOrderedMembers(listOf(second, first)).members
        assertEquals(listOf(second.memberId, first.memberId), reordered.map { it.memberId })
        assertEquals(listOf(0, 1), reordered.map { it.priority })
        assertFalse(reordered[1].enabled)
        assertEquals(0.7f, reordered[1].talkativeness)
        assertEquals(0.3f, reordered[0].talkativeness)
    }

    @Test
    fun moveMemberUsesKeysAndWorksInBothDirections() {
        val refs = List(4) { index -> GroupMemberRef(Uuid.random(), priority = index) }
        val group = Group(members = refs)
        val movedDown = group.moveMember(refs[0].memberId, refs[2].memberId)
        assertEquals(listOf(refs[1].memberId, refs[2].memberId, refs[0].memberId, refs[3].memberId),
            movedDown.members.map { it.memberId })
        assertEquals(listOf(0, 1, 2, 3), movedDown.members.map { it.priority })
        assertEquals(group, movedDown.moveMember(refs[0].memberId, refs[1].memberId))
    }

    @Test
    fun moveMemberIgnoresHeadersMissingKeysAndSelfMoves() {
        val refs = List(2) { index -> GroupMemberRef(Uuid.random(), priority = index) }
        val group = Group(members = refs)
        assertEquals(group, group.moveMember(Uuid.random(), refs[0].memberId))
        assertEquals(group, group.moveMember(refs[0].memberId, Uuid.random()))
        assertEquals(group, group.moveMember(refs[0].memberId, refs[0].memberId))
        val empty = Group()
        assertEquals(empty, empty.moveMember(refs[0].memberId, refs[1].memberId))
    }

    @Test
    fun repeatedMovesPreserveMemberParametersAndSurviveSettingsRoundTrip() {
        val members = List(4) { GroupMember(name = "Member $it") }
        val refs = members.mapIndexed { index, member ->
            GroupMemberRef(member.id, enabled = index % 2 == 0, priority = index, talkativeness = index * 0.2f)
        }
        val moved = Group(members = refs)
            .moveMember(refs[0].memberId, refs[3].memberId)
            .moveMember(refs[1].memberId, refs[2].memberId)
            .moveMember(refs[0].memberId, refs[1].memberId)
        assertEquals(listOf(refs[2].memberId, refs[0].memberId, refs[1].memberId, refs[3].memberId),
            moved.members.map { it.memberId })
        val settings = Settings(groupMembers = members, groups = listOf(moved))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings)).normalizeGroupData()
        assertEquals(moved, restored.groups.single())
        restored.groups.single().members.forEachIndexed { index, ref ->
            val original = refs.single { it.memberId == ref.memberId }
            assertEquals(index, ref.priority)
            assertEquals(original.enabled, ref.enabled)
            assertEquals(original.talkativeness, ref.talkativeness)
        }
    }

    @Test
    fun normalizationClampsTalkativenessAndRemovesDuplicateMemberReferences() {
        val first = GroupMemberRef(Uuid.random(), talkativeness = -1f)
        val second = GroupMemberRef(Uuid.random(), talkativeness = 5f)
        val third = GroupMemberRef(Uuid.random(), talkativeness = Float.NaN)
        val normalized = Group().withOrderedMembers(listOf(first, first, second, third)).members
        assertEquals(listOf(0, 1, 2), normalized.map { it.priority })
        assertEquals(listOf(0f, 1f, 0.5f), normalized.map { it.talkativeness })
    }
}
