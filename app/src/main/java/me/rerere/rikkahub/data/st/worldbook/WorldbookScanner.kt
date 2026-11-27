package me.rerere.rikkahub.data.st.worldbook

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.CharacterPersona
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.random.Random
import kotlin.uuid.Uuid

internal data class ScanContext(val character: Character, val persona: CharacterPersona? = null)
internal data class ScanResult(
    val byPosition: Map<InjectionPosition, List<PromptInjection>>,
    val outlets: Map<String, String>,
    val fired: List<String>,
)

/** Pure matching policy with explicit per-conversation state and injectable randomness. */
internal class WorldbookScanner(
    private val conversationId: Uuid,
    private val budgetChars: Int,
    private val expand: (String) -> String = { it },
    private val random: Random = Random.Default,
) {
    private data class Candidate(val key: String, val entry: PromptInjection.RegexInjection)

    fun scan(books: List<Lorebook>, chat: List<UIMessage>, extraScan: ScanContext): ScanResult {
        val state = WorldbookRuntimeState.conversation(conversationId)
        return synchronized(state) {
            if (state.characterId != extraScan.character.id) {
                state.characterId = extraScan.character.id
                state.stickyUntil.clear(); state.cooldownUntil.clear(); state.frozen = null
            }
            val turn = state.tracker.observe(chat)
            state.frozen?.takeIf { state.frozenTurn == turn }?.let { return@synchronized it }
            val candidates = books.filter { it.enabled }.flatMap { book ->
                book.entries.filter { it.enabled }.map { Candidate("${book.id}:${it.id}", it) }
            }
            val activated = linkedMapOf<String, Candidate>()
            val sticky = candidates.filter { turn <= (state.stickyUntil[it.key] ?: -1) }.mapTo(hashSetOf()) { it.key }
            val probabilityFailed = hashSetOf<String>()
            val groupWinners = hashSetOf<String>()
            var recursiveText = ""
            var used = 0
            val budget = budgetChars.takeIf { it > 0 } ?: 8000
            // Initial scan plus at most three recursive passes. Rejected budget entries never feed recursion.
            for (level in 0..3) {
                val hits = candidates.filter { c ->
                    val e = c.entry
                    when {
                        c.key in activated || c.key in probabilityFailed -> false
                        c.key in sticky -> true
                        turn <= (state.cooldownUntil[c.key] ?: -1) -> false
                        e.stDelay > turn || e.stDelayUntilRecursion > level -> false
                        level > 0 && e.stPreventRecursion -> false
                        !matches(e, chat, extraScan, recursiveText) -> false
                        e.stUseProbability && random.nextInt(100) >= e.stProbability.coerceIn(0, 100) -> {
                            probabilityFailed += c.key; false
                        }
                        else -> true
                    }
                }
                val selected = selectGroups(hits, sticky, groupWinners).sortedByDescending { it.entry.priority }
                val recursion = mutableListOf<String>()
                selected.forEach { c ->
                    val e = c.entry
                    val content = expand(e.content)
                    if (!e.stIgnoreBudget && content.length > budget - used) return@forEach
                    if (!e.stIgnoreBudget) used += content.length
                    activated[c.key] = c.copy(entry = e.copy(content = content))
                    groupWinners += groups(e)
                    if (!e.stExcludeRecursion) recursion += content
                    if (c.key !in sticky) {
                        state.stickyUntil[c.key] = maxOf(state.stickyUntil[c.key] ?: -1, turn + e.stSticky.coerceAtLeast(0))
                        state.cooldownUntil[c.key] = maxOf(state.cooldownUntil[c.key] ?: -1, turn + e.stCooldown.coerceAtLeast(0))
                    }
                }
                // A later recursion gate may still activate using previously accumulated text.
                // Skip empty intermediate levels without extending the three-level limit.
                if (recursion.isEmpty() && candidates.none { it.key !in activated && it.entry.stDelayUntilRecursion in (level + 1)..3 }) break
                recursiveText += "\n" + recursion.joinToString("\n")
            }
            val entries = activated.values.sortedByDescending { it.entry.priority }
            val outlets = entries.filter { it.entry.stPosition == 7 && it.entry.stOutletName.isNotBlank() }
                .groupBy { it.entry.stOutletName }.mapValues { (_, rows) -> rows.joinToString("\n") { it.entry.content } }
            val byPosition = entries.filter { it.entry.stPosition != 7 }.map { c ->
                val e = c.entry
                e.copy(position = when (e.stPosition) {
                    0 -> InjectionPosition.BEFORE_SYSTEM_PROMPT
                    1, 2 -> InjectionPosition.AFTER_SYSTEM_PROMPT
                    3 -> InjectionPosition.BOTTOM_OF_CHAT
                    4 -> InjectionPosition.AT_DEPTH
                    5, 6 -> InjectionPosition.TOP_OF_CHAT
                    else -> e.position
                })
            }.groupBy { it.position }
            ScanResult(byPosition, outlets, activated.keys.toList()).also { state.frozenTurn = turn; state.frozen = it }
        }
    }

    private fun matches(e: PromptInjection.RegexInjection, chat: List<UIMessage>, extra: ScanContext, recursive: String): Boolean {
        if (e.constantActive) return true
        val c = extra.character
        val text = buildList {
            addAll(chat.filter { !it.isSynthetic && it.role != MessageRole.SYSTEM }.takeLast(e.scanDepth.coerceAtLeast(1)).map { it.toText() })
            add(recursive)
            if (e.stMatchPersonaDescription) add(extra.persona?.description.orEmpty())
            if (e.stMatchCharacterDescription) add(c.description)
            if (e.stMatchCharacterPersonality) add(c.personality)
            if (e.stMatchCharacterDepthPrompt) add(c.depthPrompt)
            if (e.stMatchScenario) add(c.scenario)
            if (e.stMatchCreatorNotes) add(c.creatorNotes)
        }.joinToString("\n")
        fun hit(key: String): Boolean {
            if (key.isBlank()) return false
            val pattern = if (e.useRegex) key else Regex.escape(key)
            return runCatching {
                Regex(if (e.stMatchWholeWords) "(?<![\\p{L}\\p{N}_])(?:$pattern)(?![\\p{L}\\p{N}_])" else pattern,
                    if (e.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)).containsMatchIn(text)
            }.getOrDefault(false)
        }
        if (!e.keywords.any(::hit)) return false
        if (!e.stSelective) return true
        val secondary = e.stSecondaryKeys.map(::hit)
        return when (e.stSelectiveLogic) { 1 -> !secondary.all { it }; 2 -> secondary.none { it }; 3 -> secondary.all { it }; else -> secondary.any { it } }
    }

    private fun groups(e: PromptInjection.RegexInjection) = e.stGroup.split(',').map(String::trim).filter(String::isNotEmpty)

    private fun selectGroups(hits: List<Candidate>, sticky: Set<String>, previous: Set<String>): List<Candidate> {
        val result = hits.filter { groups(it.entry).isEmpty() }.toMutableList()
        val eliminated = hashSetOf<String>()
        val grouped = hits.flatMap { c -> groups(c.entry).map { it to c } }.groupBy({ it.first }, { it.second })
        for ((name, rows) in grouped) {
            val eligible = rows.filter { it.key !in eliminated }
            val winners = when {
                name in previous -> emptyList()
                eligible.any { it.key in sticky } -> eligible.filter { it.key in sticky }
                eligible.any { it.entry.stGroupOverride } -> eligible.filter { it.entry.stGroupOverride }
                else -> {
                    val total = eligible.sumOf { it.entry.stGroupWeight.coerceAtLeast(0).toLong() }
                    if (total <= 0) eligible.take(1) else {
                        var roll = random.nextLong(total)
                        listOfNotNull(eligible.firstOrNull { roll -= it.entry.stGroupWeight.coerceAtLeast(0); roll < 0 })
                    }
                }
            }
            eliminated += eligible.filter { it !in winners }.map { it.key }
            result += winners
        }
        return result.distinctBy { it.key }.filter { it.key !in eliminated }
    }
}
