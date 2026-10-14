package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.service.MessageQueue
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GroupPhaseBCDTest {
    private val a = GroupMember(name = "Ann")
    private val b = GroupMember(name = "Ann Lee")
    private val c = GroupMember(name = "陈明")
    private val settings = Settings(groupMembers = listOf(a, b, c))
    private val group = Group(members = listOf(GroupMemberRef(a.id), GroupMemberRef(b.id, priority = 1),
        GroupMemberRef(c.id, priority = 2)))

    @Test fun legacyDeputyAndTagPoolHavePersistedDefaults() {
        assertNull(JsonInstant.decodeFromString<Group>("""{"name":"legacy"}""").deputyMemberId)
        assertTrue(JsonInstant.decodeFromString<Settings>("{}").groupTags.isEmpty())
        val current = settings.copy(groups = listOf(group.copy(deputyMemberId = a.id)), groupTags = listOf(Tag(Uuid.random(), "tag")))
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(current))
        assertEquals(current.groups, restored.groups)
        assertEquals(current.groupTags, restored.groupTags)
        assertEquals(current.groupMembers, restored.groupMembers)
    }

    @Test fun deputyGoesLastAndDisabledDeputyNeverSpeaks() {
        assertEquals(listOf(b, c, a), group.copy(deputyMemberId = a.id).enabledMembers(settings))
        assertEquals(listOf(a, b, c), group.enabledMembers(settings))
        assertEquals(listOf(b, c), group.copy(deputyMemberId = a.id,
            members = group.members.map { if (it.memberId == a.id) it.copy(enabled = false) else it }).enabledMembers(settings))
    }

    @Test fun removingOrDeletingDeputyClearsOnlyTheInvalidGroupReference() {
        val deputyGroup = group.copy(deputyMemberId = a.id)
        val other = group.copy(id = Uuid.random(), deputyMemberId = b.id)
        assertNull(settings.copy(groups = listOf(deputyGroup), groupMembers = listOf(b, c)).normalizeGroupData().groups.single().deputyMemberId)
        val removed = deputyGroup.copy(members = deputyGroup.members.filter { it.memberId != a.id })
        val normalized = settings.copy(groups = listOf(removed, other)).normalizeGroupData()
        assertNull(normalized.groups.first().deputyMemberId)
        assertEquals(b.id, normalized.groups.last().deputyMemberId)
    }

    @Test fun tagPoolResetDoesNotTouchChatTagsOrAssistants() {
        val chatTag = Tag(Uuid.random(), "Chat")
        val assistant = Assistant(tags = listOf(chatTag.id))
        val legacy = Settings(assistantTags = listOf(chatTag), assistants = listOf(assistant),
            groupMembers = listOf(a.copy(tags = listOf(chatTag.id))), groups = listOf(group.copy(tags = listOf(chatTag.id))))
        val normalized = legacy.normalizeGroupData()
        assertEquals(legacy.assistantTags, normalized.assistantTags)
        assertEquals(legacy.assistants, normalized.assistants)
        assertTrue(normalized.groupMembers.single().tags.isEmpty())
        assertTrue(normalized.groups.single().tags.isEmpty())
    }

    @Test fun independentGroupTagsAreKeptAndChatIdsAreRejected() {
        val chat = Tag(Uuid.random(), "Same name")
        val own = Tag(Uuid.random(), "Same name")
        val current = settings.copy(assistantTags = listOf(chat), groupTags = listOf(own),
            groupMembers = listOf(a.copy(tags = listOf(chat.id, own.id, own.id))))
        assertEquals(listOf(own.id), current.normalizeGroupData().groupMembers.single().tags)
        assertEquals(listOf(chat), current.normalizeGroupData().assistantTags)
    }

    @Test fun mentionsMatchLongestNameChineseAndMultipleMembers() {
        assertEquals(listOf(b.id, c.id), extractMentions("请 @Ann Lee 和 @陈明 回答", settings.groupMembers))
        assertEquals(listOf(a.id, b.id), extractMentions("@Ann @Ann Lee @Ann", settings.groupMembers))
        assertTrue(extractMentions("a@Ann.com @Anna @Ann_foo", settings.groupMembers).isEmpty())
        assertEquals(listOf(a.id), extractMentions("@Ann：请回答", settings.groupMembers))
    }

    @Test fun mentionsLimitOnlyEnabledSpeakersAndKeepDeputyOrder() {
        val current = group.copy(deputyMemberId = a.id, members = group.members.map {
            if (it.memberId == c.id) it.copy(enabled = false) else it
        })
        assertEquals(listOf(b, a), current.speakingMembers(settings, "@Ann @Ann Lee"))
        assertEquals(listOf(b, a), current.speakingMembers(settings, "@陈明"))
        assertEquals(listOf(b), current.speakingMembers(settings, "@Ann Lee @陈明"))
        assertEquals(listOf(b, a), current.speakingMembers(settings, "normal"))
    }

    @Test fun duplicateNamesSelectAllMatchingMembersButNotMembersOfAnotherGroup() {
        val twin = a.copy(id = Uuid.random())
        assertEquals(listOf(a.id, twin.id), extractMentions("@Ann ", listOf(a, twin)))
        assertEquals(listOf(a, b, c), group.speakingMembers(settings.copy(groupMembers = settings.groupMembers + twin), "@Other"))
    }

    @Test fun fullCloneRegeneratesNodeAndConversationIdsButKeepsMessageIdsAndQuoteTargets() {
        val user = UIMessage.user("question")
        val reply = UIMessage.assistant("answer").withGroupSender(a, "member").copy(annotations = listOf(
            UIMessageAnnotation.GroupSender(a.id.toString(), a.name), user.groupQuote("Owner")))
        val source = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, title = "Title",
            messageNodes = listOf(MessageNode(messages = listOf(user)), MessageNode(messages = listOf(reply, reply.copy(id = Uuid.random())), selectIndex = 1)))
        val clone = source.copyForGroup { it }
        assertNotEquals(source.id, clone.id)
        assertTrue(clone.messageNodes.map { it.id }.intersect(source.messageNodes.map { it.id }.toSet()).isEmpty())
        assertEquals(source.messageNodes.map { it.messages }, clone.messageNodes.map { it.messages })
        assertEquals(source.messageNodes.map { it.selectIndex }, clone.messageNodes.map { it.selectIndex })
        assertEquals(source.title, clone.title)
        assertEquals(source.createAt, clone.createAt)
        assertEquals(source.updateAt, clone.updateAt)
        assertEquals(source.currentMessages.first().id.toString(), clone.currentMessages.last().annotations
            .filterIsInstance<UIMessageAnnotation.GroupQuote>().single().targetMessageId)
        assertTrue(clone.isGroupChat())
    }

    @Test fun fullCloneCopiesAttachmentsInEveryBranchAndLeavesOriginalPartsUntouched() {
        val parts = listOf(UIMessagePart.Image("file:///a.png"), UIMessagePart.Video("file:///b.mp4"),
            UIMessagePart.Audio("file:///c.wav"), UIMessagePart.Document("file:///d.txt", "d.txt", "text/plain"))
        val source = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID,
            messageNodes = listOf(MessageNode(messages = parts.map { UIMessage.user("").copy(parts = listOf(it)) })))
        var copied = 0
        val clone = source.copyForGroup { part -> copied++; when (part) {
            is UIMessagePart.Image -> part.copy(url = "file:///copy-a.png")
            is UIMessagePart.Video -> part.copy(url = "file:///copy-b.mp4")
            is UIMessagePart.Audio -> part.copy(url = "file:///copy-c.wav")
            is UIMessagePart.Document -> part.copy(url = "file:///copy-d.txt")
            else -> part
        } }
        assertEquals(4, copied)
        assertEquals(parts, source.messageNodes.single().messages.flatMap { it.parts })
        assertTrue(clone.messageNodes.single().messages.flatMap { it.parts }.none { it in parts })
    }

    @Test fun queuedQuoteSurvivesPauseAndEditAndSingleChatDefaultStaysEmpty() {
        val queue = MessageQueue()
        val quote = UIMessage.user("target").groupQuote("Owner")
        queue.pause()
        queue.enqueue(listOf(UIMessagePart.Text("body")), annotations = listOf(quote))
        val pending = queue.state.value.messages.single()
        queue.beginEdit(pending.id)
        queue.finishEdit(pending.id, listOf(UIMessagePart.Text("edited")))
        assertNull(queue.takeNext())
        queue.resume()
        assertEquals(listOf(quote), queue.takeNext()!!.annotations)
        queue.enqueue(listOf(UIMessagePart.Text("single chat")))
        assertTrue(queue.takeNext()!!.annotations.isEmpty())
    }
}
