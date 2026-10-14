package me.rerere.rikkahub.service

import kotlinx.coroutines.*
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.ai.ui.UIMessageAnnotation
import org.junit.Assert.*
import org.junit.Test

class GroupGenerationRoundTest {
    private val members = listOf(GroupMember(name = "A"), GroupMember(name = "B"), GroupMember(name = "C"))
    private fun round(index: Int = 0) = GroupGenerationRound(Settings(), Group(), members.map { GroupTurn(it) }, index)

    @Test fun laterSpeakersSeeEarlierRepliesAndCallbacksAreSerial() = runBlocking {
        val history = mutableListOf<String>()
        var active = 0
        assertTrue(round().run { turn, resuming ->
            val member = turn.member
            assertFalse(resuming)
            assertEquals(0, active++)
            assertEquals(members.takeWhile { it != member }.map { it.name }, history)
            yield()
            history += member.name
            active--
            true
        })
        assertEquals(listOf("A", "B", "C"), history)
    }

    @Test fun pendingApprovalResumesSameSpeakerWithSameSnapshot() = runBlocking {
        val round = round()
        val calls = mutableListOf<Pair<String, Boolean>>()
        val snapshot = round.settings
        assertFalse(round.run { turn, resuming ->
            val member = turn.member
            calls += member.name to resuming
            member != members[1]
        })
        assertEquals(1, round.index)
        assertTrue(round.resuming)
        assertTrue(round.run { turn, resuming ->
            calls += turn.member.name to resuming
            true
        })
        assertEquals(listOf("A" to false, "B" to false, "B" to true, "C" to false), calls)
        assertSame(snapshot, round.settings)
        assertEquals(3, round.index)
    }

    @Test fun stoppingDuringSpeakerNeverStartsLaterMembers() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val round = round()
        val calls = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            round.run { turn, _ -> val member = turn.member; calls += member.name; entered.complete(Unit); awaitCancellation() }
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(listOf("A"), calls)
        assertEquals(0, round.index)
    }

    @Test fun cancellationInsideCallbackIsCheckedBeforeAdvancing() = runBlocking {
        val round = round()
        val calls = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            round.run { turn, _ -> val member = turn.member; calls += member.name; currentCoroutineContext().cancel(); true }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf("A"), calls)
    }

    @Test fun failureStopsRoundAndLeavesPositionAtFailedMember() = runBlocking {
        val round = round()
        val calls = mutableListOf<String>()
        try {
            round.run { turn, _ -> val member = turn.member; calls += member.name; check(member != members[1]) { "failed" }; true }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("A", "B"), calls)
        assertEquals(1, round.index)
    }

    @Test fun regenerationStartsAtSelectedMemberInsteadOfRepeatingEarlierSpeakers() = runBlocking {
        val calls = mutableListOf<String>()
        assertTrue(round(1).run { turn, resuming -> assertFalse(resuming); calls += turn.member.name; true })
        assertEquals(listOf("B", "C"), calls)
    }

    @Test fun completedRoundDoesNotGenerateAgain() = runBlocking {
        val round = round()
        assertTrue(round.run { _, _ -> true })
        assertTrue(round.run { _, _ -> error("Must not repeat") })
    }

    @Test fun quoteCandidatesRemainStableAcrossApprovalResumeButRefreshForNextMember() = runBlocking {
        val round = round()
        val first = listOf(UIMessageAnnotation.GroupQuote("first", "User", "Original"))
        val later = first + UIMessageAnnotation.GroupQuote("second", "A", "New reply")
        assertSame(first, round.availableQuotes(0) { first })
        assertFalse(round.run { _, _ -> false })
        assertSame(first, round.availableQuotes(0) { later })
        assertTrue(round.run { turn, _ -> val member = turn.member;
            if (member == members[1]) assertSame(later, round.availableQuotes(round.index) { later })
            true
        })
    }

    @Test fun dispatchReordersWithoutDuplicatingAndSummarisesOnce() = runBlocking {
        val deputy = members[0]
        val round = GroupGenerationRound(Settings(), Group(deputyMemberId = deputy.id),
            listOf(GroupTurn(deputy, GroupTurnKind.DISPATCH), GroupTurn(members[1]), GroupTurn(members[2])))
        val calls = mutableListOf<Pair<String, GroupTurnKind>>()
        assertTrue(round.run { turn, _ ->
            calls += turn.member.name to turn.kind
            if (turn.kind == GroupTurnKind.DISPATCH) {
                assertTrue(round.dispatch(listOf(members[2], members[2])))
                assertFalse(round.dispatch(listOf(members[1])))
            }
            true
        })
        assertEquals(listOf("A" to GroupTurnKind.DISPATCH, "C" to GroupTurnKind.SPEAK,
            "B" to GroupTurnKind.SPEAK, "A" to GroupTurnKind.SUMMARY), calls)
    }

    @Test fun dispatchAndSummaryPauseIndependentlyAndUseDifferentQuoteLists() = runBlocking {
        val deputy = members[0]
        val round = GroupGenerationRound(Settings(), Group(), listOf(GroupTurn(deputy, GroupTurnKind.DISPATCH), GroupTurn(members[1])))
        val first = listOf(UIMessageAnnotation.GroupQuote("first", "Owner", "Question"))
        val later = first + UIMessageAnnotation.GroupQuote("second", "B", "Answer")
        assertSame(first, round.availableQuotes(0) { first })
        assertEquals(4, round.responseStart(0) { 4 })
        assertFalse(round.run { _, resumed -> assertFalse(resumed); false })
        assertEquals(0, round.index)
        assertEquals(4, round.responseStart(0) { 8 })
        assertFalse(round.run { turn, resumed ->
            when (turn.kind) {
                GroupTurnKind.DISPATCH -> { assertTrue(resumed); assertSame(first, round.availableQuotes(0) { later }); round.dispatch(listOf(members[1])); true }
                GroupTurnKind.SPEAK -> true
                GroupTurnKind.SUMMARY -> { assertFalse(resumed); assertEquals(10, round.responseStart(round.index) { 10 }); assertSame(later, round.availableQuotes(round.index) { later }); false }
            }
        })
        assertEquals(2, round.index)
        assertEquals(2, round.turns.indexOfLast { it.member.id == deputy.id })
        assertTrue(round.run { turn, resumed -> assertEquals(GroupTurnKind.SUMMARY, turn.kind); assertTrue(resumed); true })
    }

    @Test fun absentDispatchKeepsOriginalSuffixWithoutSummary() = runBlocking {
        val round = GroupGenerationRound(Settings(), Group(), listOf(GroupTurn(members[0], GroupTurnKind.DISPATCH), GroupTurn(members[1])))
        assertFalse(round.dispatch(emptyList()))
        assertFalse(round.dispatch(listOf(members[0])))
        val kinds = mutableListOf<GroupTurnKind>()
        assertTrue(round.run { turn, _ -> kinds += turn.kind; true })
        assertEquals(listOf(GroupTurnKind.DISPATCH, GroupTurnKind.SPEAK), kinds)
    }
}
