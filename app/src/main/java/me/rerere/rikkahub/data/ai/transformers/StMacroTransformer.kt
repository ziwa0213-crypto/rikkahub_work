package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.st.macro.*
import me.rerere.rikkahub.data.st.regex.*
import me.rerere.rikkahub.data.st.worldbook.*
import kotlin.uuid.Uuid

/** A turn owns macro expansion caches; tool steps reuse them rather than repeating set/inc side effects. */
internal class CharacterStTurn(
    private val character: Character,
    private val settings: Settings,
    private val conversationId: Uuid,
    private val registry: MacroStoreRegistry,
    initialHistory: List<UIMessage>,
) : InputMessageTransformer {
    private var turnHistory = initialHistory
    private var historyCaptured = false
    /** Capture before request trimming and before any compression can hide the real user ID. */
    fun captureHistory(messages: List<UIMessage>) {
        if (!historyCaptured) { turnHistory = messages; historyCaptured = true }
    }
    private val persona = settings.characterPersonas.firstOrNull { it.id == character.personaId }
    private val cache = mutableMapOf<String, String>()
    private var outlets: Map<String, String> = emptyMap()
    private val scripts = mergeActiveRegexScripts(character.stRegexScripts, settings.stRegexScripts)
    private val variables = VariableStores(
        local = registry.store("conversation:$conversationId") { settings.stVariables["conversation:$conversationId"].orEmpty() },
        global = registry.store("global") { settings.stVariables["global"].orEmpty() },
    )

    private fun environment(chat: List<UIMessage>) = characterMacroEnvironment(character, settings, chat, variables, outlets)

    private fun expand(text: String, chat: List<UIMessage>): String {
        CharacterMacroBootstrap.ensureInitialized()
        return MacroEngine.evaluate(text, environment(chat))
    }

    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        return transformMessages(messages, ctx.assistant.lorebookIds)
    }

    internal fun transformMessages(messages: List<UIMessage>, lorebookIds: Set<Uuid> = character.lorebookIds): List<UIMessage> {
        val books = settings.lorebooks.filter { it.id in lorebookIds }
        // Outlets are resolved after scanning. Protect their invocations so scanning does not
        // replace not-yet-published outlets with empty strings or evaluate variable macros twice.
        val protected = mutableMapOf<String, String>()
        val scan = WorldbookScanner(conversationId, character.stWorldbookBudgetChars ?: settings.stWorldbookBudgetChars, expand = { text ->
            val safe = OUTLET.replace(text) { match ->
                val token = "\uE000${protected.size}\uE001"
                protected[token] = match.value
                token
            }
            var expanded = expand(safe, turnHistory)
            protected.forEach { (token, original) -> expanded = expanded.replace(token, original) }
            RegexScriptEngine.getRegexedString(expanded, scripts, RegexPlacement.WORLD_INFO, isPrompt = true,
                substitute = { expand(it, turnHistory) })
        }).scan(books, turnHistory, ScanContext(character, persona))
        outlets = scan.outlets
        repeat(3) { outlets = outlets.mapValues { (_, value) -> resolveOutlets(value) } }
        val expanded = messages.mapIndexed { messageIndex, message -> message.copy(parts = message.parts.mapIndexed { index, part ->
            if (part !is UIMessagePart.Text) part else {
                // The generated system message gets a new id for every tool step; use its source
                // text instead. Real messages retain their stable ids and attachment parts.
                val key = "${if (message.isSynthetic) "synthetic:${message.role}:$messageIndex" else message.id.toString()}:$index"
                part.copy(text = cache.getOrPut(key) { expand(part.text, turnHistory) })
            }
        }) }
        val rows = scan.byPosition.mapValues { (_, rows) -> rows.map { row ->
            (row as me.rerere.rikkahub.data.model.PromptInjection.RegexInjection).copy(content = resolveOutlets(row.content))
        } }
        val injected = insertKeepingRole(expanded, rows)
        val depth = cache.getOrPut("depth:${character.depthPrompt}") { expand(character.depthPrompt, turnHistory) }
        val final = applyCharacterDepthPrompt(injected, character.copy(depthPrompt = depth))
        return final.mapIndexed { index, message ->
            // General scripts already ran when the real message was stored; only prompt flags
            // run again here. This avoids applying a non-idempotent replacement twice.
            val active = if (message.isSynthetic) scripts else scripts.filter { it.promptOnly }
            applyStRegex(message, active, isPrompt = true, depth = final.lastIndex - index) { value ->
                cache.getOrPut("regex:$index:$value") { expand(value, turnHistory) }
            }
        }
    }

    fun transformOutput(message: UIMessage): UIMessage = applyStRegex(message, scripts, depth = 0) { expand(it, turnHistory) }

    private fun resolveOutlets(text: String) = OUTLET.replace(text) { outlets[it.groupValues[1].trim()].orEmpty() }

    companion object { private val OUTLET = Regex("\\{\\{outlet::([^{}]+)}}", RegexOption.IGNORE_CASE) }
}
