package me.rerere.rikkahub.data.st.worldbook

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection.RegexInjection
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.uuid.Uuid

class WorldbookScannerTest {
    private val character = Character(name = "Alice")
    private fun entry(text: String = "entry", vararg keys: String) = RegexInjection(content = text, keywords = keys.toList(), stPosition = 4, role = MessageRole.SYSTEM)
    private fun scanner(id: Uuid = Uuid.random(), budget: Int = 8000) = WorldbookScanner(id, budget, random = Random(7))
    private fun ScanResult.texts() = byPosition.values.flatten().map { it.content }

    @Test fun keywordsSelectiveWholeWordsAndExtraFields() {
        val rows = listOf(entry("yes", "cat").copy(stMatchWholeWords = true), entry("no", "missing"),
            entry("selective", "cat").copy(stSelective = true, stSecondaryKeys = listOf("blue")),
            entry("description", "forest").copy(stMatchCharacterDescription = true))
        val scan = scanner().scan(listOf(Lorebook(entries = rows)), listOf(UIMessage.user("cat blue")),
            ScanContext(character.copy(description = "forest")))
        assertEquals(listOf("yes", "selective", "description"), scan.texts())
        assertTrue(scanner().scan(listOf(Lorebook(entries = listOf(rows.first()))), listOf(UIMessage.user("cattle")), ScanContext(character)).texts().isEmpty())
    }

    @Test fun recursionHonorsLimitsAndFlags() {
        val a = entry("B", "start")
        val b = entry("C", "B")
        val c = entry("D", "C")
        val d = entry("E", "D")
        val e = entry("end", "E")
        fun scan(rows: List<RegexInjection>) = scanner().scan(listOf(Lorebook(entries = rows)), listOf(UIMessage.user("start")), ScanContext(character)).texts()
        assertEquals(listOf("B", "C", "D", "E"), scan(listOf(a, b, c, d, e)))
        assertEquals(listOf("B"), scan(listOf(a.copy(stExcludeRecursion = true), b)))
        assertEquals(listOf("B"), scan(listOf(a, b.copy(stPreventRecursion = true))))
        assertEquals(listOf("B", "C"), scan(listOf(a, b.copy(stDelayUntilRecursion = 1))))
        assertEquals(listOf("B", "C"), scan(listOf(a, b.copy(stDelayUntilRecursion = 3))))
    }

    @Test fun probabilityAndBudgetAndDisabledBooks() {
        val rows = listOf(entry("never", "cat").copy(stUseProbability = true, stProbability = 0),
            entry("high", "cat").copy(priority = 10), entry("too-long", "cat"),
            entry("ignored", "cat").copy(stIgnoreBudget = true))
        val result = scanner(budget = 4).scan(listOf(Lorebook(entries = rows)), listOf(UIMessage.user("cat")), ScanContext(character))
        assertEquals(listOf("high", "ignored"), result.texts())
        assertTrue(scanner().scan(listOf(Lorebook(enabled = false, entries = rows)), listOf(UIMessage.user("cat")), ScanContext(character)).texts().isEmpty())
    }

    @Test fun stickyDoesNotRenewAndCooldownIsInclusiveAndSameTurnIsFrozen() {
        val s = scanner()
        val book = Lorebook(entries = listOf(entry("active", "cat").copy(stSticky = 3, stCooldown = 2)))
        for (turn in 1..8) {
            val message = UIMessage.user(if (turn == 1) "cat" else "nothing")
            val result = s.scan(listOf(book), listOf(message), ScanContext(character))
            assertEquals(turn <= 4, result.texts().isNotEmpty())
            repeat(3) {
                assertEquals(result, s.scan(listOf(book), listOf(message, UIMessage.assistant("tool step"), UIMessage.user("cat").copy(isSynthetic = true)), ScanContext(character)))
            }
        }
        val cool = scanner()
        val cooling = book.copy(entries = listOf(book.entries.single().copy(stSticky = 0)))
        for (turn in 1..4) {
            val result = cool.scan(listOf(cooling), listOf(UIMessage.user("cat")), ScanContext(character))
            assertEquals(turn == 1 || turn == 4, result.texts().isNotEmpty())
        }
    }

    @Test fun neverMatchedHasNoCooldownAndConversationsAreIsolated() {
        val book = Lorebook(entries = listOf(entry("active", "cat").copy(stSticky = 2, stCooldown = 2)))
        val a = scanner()
        assertTrue(a.scan(listOf(book), listOf(UIMessage.user("nothing")), ScanContext(character)).texts().isEmpty())
        assertFalse(a.scan(listOf(book), listOf(UIMessage.user("cat")), ScanContext(character)).texts().isEmpty())
        assertTrue(scanner().scan(listOf(book), listOf(UIMessage.user("nothing")), ScanContext(character)).texts().isEmpty())
    }

    @Test fun outletsAndWeightedGroupsDoNotDuplicate() {
        val rows = listOf(entry("one", "cat").copy(stGroup = "g", stGroupWeight = 0),
            entry("two", "cat").copy(stGroup = "g", stGroupWeight = 100),
            entry("out", "cat").copy(stPosition = 7, stOutletName = "details"))
        val result = scanner().scan(listOf(Lorebook(entries = rows)), listOf(UIMessage.user("cat")), ScanContext(character))
        assertEquals(listOf("two"), result.texts())
        assertEquals(mapOf("details" to "out"), result.outlets)
    }

    @Test fun turnIdentitySurvivesTrimmingAndIgnoresSyntheticMessages() {
        val tracker = TurnTracker()
        repeat(6) { index ->
            val message = UIMessage.user("turn $index")
            assertEquals(index + 1, tracker.observe(listOf(message)))
            assertEquals(index + 1, tracker.observe(listOf(message, UIMessage.user("injected").copy(isSynthetic = true))))
        }
        assertEquals(6, tracker.observe(listOf(UIMessage.assistant("no user left"))))
    }
}
