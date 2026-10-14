package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GroupMemberProfileTest {
    private val member = GroupMember(name = "Ann", avatar = Avatar.Emoji("A"))
    private fun group(nickname: String = "", avatar: Avatar? = null) =
        Group(members = listOf(GroupMemberRef(member.id, nickname = nickname, avatar = avatar)))

    @Test fun `old settings retain default profile and new values survive json roundtrip`() {
        val legacy = """{"groupMembers":[{"id":"${member.id}","name":"Ann"}],"groups":[{"members":[{"memberId":"${member.id}"}]}]}"""
        val restored = JsonInstant.decodeFromString<Settings>(legacy)
        val ref = restored.groups.single().members.single()
        assertEquals("", ref.nickname)
        assertNull(ref.avatar)
        assertEquals("Ann", restored.groups.single().displayNameOf(restored.groupMembers.single()))
        val current = restored.copy(groups = listOf(group("小明", Avatar.Image("file:///private/avatar.png"))))
        val roundtrip = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(current))
        assertEquals(current.groups, roundtrip.groups)
        assertEquals(current.groupMembers, roundtrip.groupMembers)
    }

    @Test fun `overrides stay within one group and default avatar is distinct from unset`() {
        val first = group("小明", Avatar.Emoji("一"))
        val second = group("AI 助手", Avatar.Dummy)
        assertEquals("小明", first.displayNameOf(member))
        assertEquals(Avatar.Emoji("一"), first.displayAvatarOf(member))
        assertEquals("AI 助手", second.displayNameOf(member))
        assertEquals(Avatar.Dummy, second.displayAvatarOf(member))
        assertEquals(member.name, group().displayNameOf(member))
        assertEquals(member.avatar, group().displayAvatarOf(member))
        assertEquals("Ann", member.name)
        assertEquals(Avatar.Emoji("A"), member.avatar)
        assertEquals(first.members.single(), first.memberRefOf(member.id.toString()))
        assertNull(first.memberRefOf("missing"))
    }

    @Test fun `history follows current nickname without changing stored annotations and clearing restores base name`() {
        val stored = UIMessage.assistant("body").withGroupSender(member.copy(name = "Old nickname"), "Old nickname")
        val sender = stored.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().single()
        assertEquals("New nickname", group("New nickname").displayNameOf(sender, member))
        assertEquals(member.name, group().displayNameOf(sender, member))
        assertEquals("Old nickname", (null as Group?).displayNameOf(sender, member))
        assertEquals("Old nickname", Group().displayNameOf(sender, member))
        assertEquals("Old nickname", group().displayNameOf(sender, null))
        assertEquals("New nickname", group("New nickname").displayNameOf(sender, null))
        assertEquals(sender, stored.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().single())
        assertEquals("Old nickname", sender.memberName)
    }

    @Test fun `new replies persist nickname and strip only that speakers first text label`() {
        val current = group("小明", Avatar.Emoji("名"))
        val assistant = member.toAssistant(current)
        assertEquals("小明", assistant.name)
        assertEquals(Avatar.Emoji("名"), assistant.avatar)
        val parts = listOf(UIMessagePart.Reasoning("keep"), UIMessagePart.Text("[小明] answer"))
        val reply = UIMessage(role = MessageRole.ASSISTANT, parts = parts)
            .withGroupSender(member.copy(name = current.displayNameOf(member)), current.displayNameOf(member))
            .stripSelfSpeakerPrefix(current.displayNameOf(member))
        assertEquals("小明", reply.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().single().memberName)
        assertEquals(parts.first(), reply.parts.first())
        assertEquals(UIMessagePart.Text("answer"), reply.parts.last())
        val restored = JsonInstant.decodeFromString<UIMessage>(JsonInstant.encodeToString(reply))
        assertEquals(reply, restored)
        val user = UIMessage.user("unchanged")
        assertEquals(user, user.withGroupSender(member, "小明"))
    }

    @Test fun `mentions recognize base names and aliases with longest match and shared aliases`() {
        val other = GroupMember(name = "Other")
        val aliases = mapOf(member.id to "Ann Lee", other.id to "同名")
        assertEquals(listOf(member.id), extractMentions("@Ann Lee please", listOf(member, other), aliases))
        assertEquals(listOf(member.id), extractMentions("@Ann please", listOf(member, other), aliases))
        assertEquals(listOf(other.id), extractMentions("@同名，继续", listOf(member, other), aliases))
        assertTrue(extractMentions("mail@Ann Lee", listOf(member), aliases).isEmpty())
        assertEquals(listOf(member.id, other.id), extractMentions("@同名", listOf(member, other),
            mapOf(member.id to "同名", other.id to "同名")))
        assertEquals(listOf(member.id), extractMentions("@Ann", listOf(member), mapOf(member.id to " ")))
    }

    @Test fun `selection and deputy dispatch match current group aliases only and preserve ordering`() {
        val other = GroupMember(name = "Other")
        val deputy = GroupMember(name = "Deputy")
        val outsider = GroupMember(name = "Outside")
        val settings = Settings(groupMembers = listOf(member, other, deputy, outsider))
        val current = Group(enableDeputyDispatch = true, deputyMemberId = deputy.id,
            members = listOf(GroupMemberRef(member.id, nickname = "小明", talkativeness = 0.3f),
                GroupMemberRef(other.id, nickname = "小红", priority = 1, talkativeness = 0.8f),
                GroupMemberRef(deputy.id, nickname = "队长", priority = 2)))
        assertEquals(listOf(member), current.speakingMembers(settings, "@小明 请回答"))
        assertEquals(listOf(other, member), current.resolveDispatch(settings, "@小明 @小红").members)
        assertEquals(listOf(other), current.resolveDispatch(settings, "@队长 @小红").members)
        assertFalse(current.resolveDispatch(settings, "@Outside @不属于本群").matched)
        val disabled = current.copy(members = current.members.map {
            if (it.memberId == member.id) it.copy(enabled = false) else it
        })
        assertFalse(disabled.resolveDispatch(settings, "@小明").matched)
        assertEquals(disabled.enabledMembers(settings), disabled.speakingMembers(settings, "@小明"))
        assertEquals(current.enabledMembers(settings), current.speakingMembers(settings, "@Outside"))
    }

    @Test fun `normalization reorder and copy retain per group profiles`() {
        val other = GroupMember(name = "Other")
        val current = group(" 小明 ", Avatar.Emoji("名")).copy(members =
            group(" 小明 ", Avatar.Emoji("名")).members + GroupMemberRef(other.id))
        val normalized = Settings(groupMembers = listOf(member, other), groups = listOf(current)).normalizeGroupData()
        val moved = normalized.groups.single().moveMember(member.id, other.id)
        assertEquals("小明", moved.memberRefOf(member.id)!!.nickname)
        assertEquals(Avatar.Emoji("名"), moved.memberRefOf(member.id)!!.avatar)
        assertEquals(moved.members, moved.copy(id = Uuid.random()).members)
        assertEquals(listOf(member, other), normalized.groupMembers)
    }
}
