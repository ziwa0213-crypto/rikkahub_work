package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

@Serializable
data class Group(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val members: List<GroupMemberRef> = emptyList(),
    val tags: List<Uuid> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = emptyList(),
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    val enabledSkills: Set<String> = emptySet(),
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val background: String? = null,
    val backgroundOpacity: Float = 1.0f,
    val useGradientBackground: Boolean = false,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val toolApprovalMode: ToolApprovalMode = ToolApprovalMode.Partial,
    val deputyMemberId: Uuid? = null,
    val isPinned: Boolean = false,
    val enableMemory: Boolean = true,
    val systemPrompt: String = "",
    val conversationIds: List<Uuid> = emptyList(),
    val enableDeputyDispatch: Boolean = false,
    val enableAutoCompress: Boolean = true,
    val compressThresholdRatio: Float = 0.7f,
    val compressByMessageCount: Boolean = true,
    val compressMessageCount: Int = 40,
    val compressKeepRecent: Int = 32,
    val compressTargetTokens: Int = 2000,
    val compressContextLength: Int = 0,
    val enableTieredCompress: Boolean = true,
    val workspaceId: Uuid? = null,
    val enableRecentChatsReference: Boolean = true,
    val enableWebSearch: Boolean = true,
    val enableTimeReminder: Boolean = false,
    val timeReminderIntervalMinutes: Int = 60,
)

@Serializable
data class GroupMemberRef(
    val memberId: Uuid,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val talkativeness: Float = 0.5f,
    val nickname: String = "",
    val avatar: Avatar? = null,
)

fun Group.withOrderedMembers(members: List<GroupMemberRef>) = copy(
    members = members.distinctBy { it.memberId }.mapIndexed { index, ref ->
        ref.copy(
            priority = index,
            talkativeness = if (ref.talkativeness.isFinite()) ref.talkativeness.coerceIn(0f, 1f) else 0.5f,
            nickname = ref.nickname.trim(),
        )
    },
)

fun Group.moveMember(fromId: Uuid, toId: Uuid): Group {
    val fromIndex = members.indexOfFirst { it.memberId == fromId }
    val toIndex = members.indexOfFirst { it.memberId == toId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return this
    return withOrderedMembers(members.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
}

fun Settings.normalizeGroupData(validWorkspaceIds: Set<String>? = null): Settings {
    val validTags = groupTags.map { it.id }.toSet()
    val members = groupMembers.distinctBy { it.id }.map { member ->
        member.copy(tags = member.tags.distinct().filter { it in validTags })
    }
    val validMembers = members.map { it.id }.toSet()
    return copy(
        groupMembers = members,
        groups = groups.distinctBy { it.id }.map { group ->
            val safeMembers = group.members.filter { it.memberId in validMembers }
            group.copy(tags = group.tags.distinct().filter { it in validTags },
                workspaceId = group.workspaceId?.takeIf { validWorkspaceIds == null || it.toString() in validWorkspaceIds },
                conversationIds = group.conversationIds.distinct(),
                deputyMemberId = group.deputyMemberId?.takeIf { id -> safeMembers.any { it.memberId == id } })
                .withOrderedMembers(safeMembers)
        },
    )
}
