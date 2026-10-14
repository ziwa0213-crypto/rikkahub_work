package me.rerere.rikkahub.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.ai.ui.UIMessageAnnotation

internal enum class GroupTurnKind { SPEAK, DISPATCH, SUMMARY }
internal data class GroupTurn(val member: GroupMember, val kind: GroupTurnKind = GroupTurnKind.SPEAK)

// A paused approval keeps the same settings, quote candidates and speaker position.
internal class GroupGenerationRound(
    val settings: Settings,
    val group: Group,
    turns: List<GroupTurn>,
    var index: Int = 0,
    var resuming: Boolean = false,
) {
    private val mutableTurns = turns.toMutableList()
    val turns: List<GroupTurn> get() = mutableTurns
    val members: List<GroupMember> get() = turns.map { it.member }.distinctBy { it.id }
    private val quoteLists = mutableMapOf<Int, List<UIMessageAnnotation.GroupQuote>>()
    private val responseStarts = mutableMapOf<Int, Int>()
    fun responseStart(turnIndex: Int, build: () -> Int): Int = responseStarts.getOrPut(turnIndex, build)
    fun availableQuotes(turnIndex: Int, build: () -> List<UIMessageAnnotation.GroupQuote>) =
        quoteLists.getOrPut(turnIndex, build)

    private var dispatched = false
    fun dispatch(members: List<GroupMember>): Boolean {
        if (dispatched || members.isEmpty() || turns.getOrNull(index)?.kind != GroupTurnKind.DISPATCH) return false
        val deputy = turns[index].member
        val named = members.filter { it.id != deputy.id }.distinctBy { it.id }
        if (named.isEmpty()) return false
        dispatched = true
        // Reorder the unstarted suffix rather than append duplicates. Each member speaks once.
        val namedIds = named.map { it.id }.toSet()
        val remaining = turns.drop(index + 1).filter { it.member.id !in namedIds && it.member.id != deputy.id }
        mutableTurns.subList(index + 1, mutableTurns.size).clear()
        mutableTurns += named.map { GroupTurn(it) } + remaining + GroupTurn(deputy, GroupTurnKind.SUMMARY)
        return true
    }

    suspend fun run(generate: suspend (GroupTurn, Boolean) -> Boolean): Boolean {
        while (index < turns.size) {
            currentCoroutineContext().ensureActive()
            if (!generate(turns[index], resuming)) {
                resuming = true
                return false
            }
            currentCoroutineContext().ensureActive()
            index++
            resuming = false
        }
        return true
    }
}
