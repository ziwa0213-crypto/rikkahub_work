package me.rerere.rikkahub.data.st.`import`

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection.RegexInjection
import me.rerere.rikkahub.data.st.regex.parseCardRegexScripts
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

internal data class ParseResult(
    val card: TavernCharacterData,
    val imageBytes: ByteArray?,
    val embeddedBooks: List<TavernEmbeddedBook>,
    val rawCardJson: String,
    val rawCardTruncated: Boolean,
    val rawEntries: List<String>,
) {
    fun materialize(avatar: Avatar = Avatar.Dummy, embeddedBookName: String = card.name): Pair<Character, List<Lorebook>> {
        val books = embeddedBooks.map { book -> Lorebook(
            name = book.name.ifBlank { embeddedBookName }, description = book.description,
            entries = book.entries.mapIndexed { index, entry -> entry.toInjection(rawEntries.getOrElse(index) { "" }) },
        ) }
        val now = System.currentTimeMillis()
        return Character(
            name = card.name, nickname = card.nickname, avatar = avatar, description = card.description,
            personality = card.personality, scenario = card.scenario, firstMessage = card.firstMessage,
            alternateGreetings = card.alternateGreetings, exampleMessages = card.mesExample,
            characterSystemPrompt = card.systemPrompt, postHistoryInstructions = card.postHistoryInstructions,
            creatorNotes = card.creatorNotes, characterVersion = card.characterVersion, tags = card.tags,
            rawExtensions = card.extensionsRaw, rawCardJson = rawCardJson, rawCardTruncated = rawCardTruncated,
            depthPrompt = card.depthPrompt, depthPromptDepth = card.depthPromptDepth.coerceIn(1, 20),
            depthPromptRole = card.depthPromptRole, stRegexScripts = parseCardRegexScripts(objectOrEmpty(card.extensionsRaw)),
            lorebookIds = books.map { it.id }.toSet(), createdAt = now, updatedAt = now,
        ) to books
    }
}

/** Pure JVM codec. Original metadata is used only here, never as a prompt/runtime source. */
internal object CharacterCardCodec {
    const val RAW_LIMIT_BYTES = 256 * 1024
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(bytes: ByteArray, fileName: String): ParseResult? = runCatching {
        val png = bytes.take(8).toByteArray().contentEquals(signature)
        if (!png && !fileName.endsWith(".json", true)) return null
        val text = if (png) {
            val candidates = chunks(bytes).mapNotNull { chunk -> metadata(chunk)?.let { (key, value) ->
                if (key !in setOf("chara", "ccv3")) null else runCatching {
                    val decoded = Base64.getMimeDecoder().decode(value).toString(Charsets.UTF_8)
                    val root = json.parseToJsonElement(decoded) as? JsonObject ?: return@runCatching null
                    if (root.string("spec") !in setOf("chara_card_v2", "chara_card_v3") || root["data"] !is JsonObject ||
                        (root["data"]!!.jsonObject["name"] as? JsonPrimitive)?.isString != true) null
                    else Triple(if (key == "ccv3") 1 else 0, decoded, root)
                }.getOrNull()
            } }.sortedByDescending { it.first }
            candidates.firstOrNull()?.second ?: return null
        } else bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: return null
        if ((data["name"] as? JsonPrimitive)?.isString != true || root.string("spec") !in setOf("chara_card_v2", "chara_card_v3")) return null
        val ext = data["extensions"] as? JsonObject ?: JsonObject(emptyMap())
        val depth = ext["depth_prompt"] as? JsonObject ?: JsonObject(emptyMap())
        val bookObject = data["character_book"] as? JsonObject
        val rawEntries = bookObject?.get("entries").entryObjects()
        val book = bookObject?.let { parseBook(it) }
        val card = TavernCharacterData(
            spec = root.string("spec"), specVersion = root.string("spec_version"), name = data.string("name"),
            description = data.string("description"), personality = data.string("personality"), scenario = data.string("scenario"),
            firstMessage = data.string("first_mes"), alternateGreetings = data["alternate_greetings"].strings(),
            mesExample = data.string("mes_example"), systemPrompt = data.string("system_prompt"), creator = data.string("creator"),
            creatorNotes = data.string("creator_notes"), characterVersion = data.string("character_version"),
            tags = data["tags"].strings(), postHistoryInstructions = data.string("post_history_instructions"),
            extensions = ext.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }, extensionsRaw = ext.toString(),
            assets = (data["assets"] as? JsonArray)?.mapNotNull { a -> (a as? JsonObject)?.let {
                TavernAsset(it.string("type"), it.string("name"), it.string("uri"), it.string("ext"))
            } }.orEmpty(), groupOnlyGreetings = data["group_only_greetings"].strings(), nickname = data.string("nickname"),
            creatorNotesMultilingual = data["creator_notes_multilingual"]?.toString().orEmpty(), source = data["source"].strings(),
            creationDate = data["creation_date"]?.toString().orEmpty(), modificationDate = data["modification_date"]?.toString().orEmpty(),
            depthPrompt = depth.string("prompt"), depthPromptDepth = depth.int("depth", 4), depthPromptRole = roleOf(depth["role"]), embeddedBook = book,
        )
        val oversized = text.toByteArray().size > RAW_LIMIT_BYTES
        ParseResult(card, bytes.takeIf { png }, listOfNotNull(book), if (oversized) "" else text, oversized,
            rawEntries.map { it.toString().takeIf { raw -> !oversized && raw.toByteArray().size <= RAW_LIMIT_BYTES }.orEmpty() })
    }.getOrNull()

    fun encodeJson(character: Character, books: List<Lorebook> = emptyList()): String = encodeObject(character, books).toString()

    private fun encodeObject(character: Character, books: List<Lorebook>): JsonObject {
        val root = objectOrEmpty(character.rawCardJson).toMutableMap()
        val data = (root["data"] as? JsonObject).orEmpty().toMutableMap()
        val fields = mapOf("name" to character.name, "nickname" to character.nickname, "description" to character.description,
            "personality" to character.personality, "scenario" to character.scenario, "first_mes" to character.firstMessage,
            "mes_example" to character.exampleMessages, "system_prompt" to character.characterSystemPrompt,
            "post_history_instructions" to character.postHistoryInstructions, "creator_notes" to character.creatorNotes,
            "character_version" to character.characterVersion)
        fields.forEach { (key, value) -> data[key] = JsonPrimitive(value) }
        data["alternate_greetings"] = character.alternateGreetings.jsonArray()
        data["tags"] = character.tags.jsonArray()
        val ext = objectOrEmpty(character.rawExtensions).toMutableMap()
        val dp = (ext["depth_prompt"] as? JsonObject).orEmpty().toMutableMap()
        dp["prompt"] = JsonPrimitive(character.depthPrompt); dp["depth"] = JsonPrimitive(character.depthPromptDepth)
        dp["role"] = JsonPrimitive(character.depthPromptRole); ext["depth_prompt"] = JsonObject(dp)
        val originalScripts = ((ext["regex_scripts"] ?: ext["regexScripts"]) as? JsonArray).orEmpty()
        ext["regex_scripts"] = JsonArray(character.stRegexScripts.mapIndexed { index, script ->
            val old = originalScripts.filterIsInstance<JsonObject>().firstOrNull { it.string("id").isNotBlank() && it.string("id") == script.id }
                ?: (originalScripts.getOrNull(index) as? JsonObject)?.takeIf { it.string("id").isBlank() }
            JsonObject(old.orEmpty() + json.encodeToJsonElement(script).jsonObject)
        })
        data["extensions"] = JsonObject(ext)
        if (books.isNotEmpty()) {
            val book = (data["character_book"] as? JsonObject).orEmpty().toMutableMap()
            book["name"] = JsonPrimitive(books.first().name); book["description"] = JsonPrimitive(books.first().description)
            val entries = books.flatMap { it.entries }
            val encoded = entries.mapIndexed { index, entry -> encodeEntry(entry, index) }
            // Preserve the input array/map shape and unknown nested values. Removed entries stay removed.
            book["entries"] = if (book["entries"] is JsonObject) JsonObject(encoded.mapIndexed { index, value ->
                value.string("id").ifBlank { index.toString() } to value
            }.toMap()) else JsonArray(encoded)
            data["character_book"] = JsonObject(book)
        } else if (character.lorebookIds.isEmpty()) data.remove("character_book")
        root["spec"] = JsonPrimitive("chara_card_v3"); root["spec_version"] = JsonPrimitive("3.0"); root["data"] = JsonObject(data)
        return JsonObject(root)
    }

    fun encodeWorldbook(book: Lorebook): String = buildJsonObject {
        put("name", book.name); put("description", book.description)
        put("entries", JsonArray(book.entries.mapIndexed { index, entry -> encodeEntry(entry, index) }))
    }.toString()

    fun encodePng(sourceImage: ByteArray?, character: Character, books: List<Lorebook> = emptyList()): ByteArray {
        require(sourceImage != null) { "PNG image required" }
        val original = chunks(sourceImage)
        val v3 = encodeObject(character, books)
        val v2 = JsonObject(v3 + ("spec" to JsonPrimitive("chara_card_v2")) + ("spec_version" to JsonPrimitive("2.0")))
        return ByteArrayOutputStream().apply {
            write(signature)
            for (chunk in original) {
                if (metadata(chunk)?.first in setOf("chara", "ccv3")) continue
                if (chunk.type == "IEND") {
                    write(makeChunk("tEXt", "chara\u0000".toByteArray() + Base64.getEncoder().encode(v2.toString().toByteArray())))
                    write(makeChunk("tEXt", "ccv3\u0000".toByteArray() + Base64.getEncoder().encode(v3.toString().toByteArray())))
                }
                write(chunk.original)
            }
        }.toByteArray()
    }

    private data class Chunk(val type: String, val data: ByteArray, val original: ByteArray)
    private fun chunks(bytes: ByteArray): List<Chunk> {
        require(bytes.size >= 20 && bytes.copyOfRange(0, 8).contentEquals(signature))
        val result = mutableListOf<Chunk>()
        var cursor = 8
        while (cursor <= bytes.size - 12) {
            val size = ByteBuffer.wrap(bytes, cursor, 4).int
            require(size >= 0 && size <= bytes.size - cursor - 12)
            val end = cursor + size + 12
            val type = bytes.copyOfRange(cursor + 4, cursor + 8).toString(Charsets.US_ASCII)
            val checksum = CRC32().apply { update(bytes, cursor + 4, size + 4) }.value
            require(checksum == (ByteBuffer.wrap(bytes, end - 4, 4).int.toLong() and 0xffffffffL))
            result += Chunk(type, bytes.copyOfRange(cursor + 8, end - 4), bytes.copyOfRange(cursor, end))
            cursor = end
            if (type == "IEND") break
        }
        require(result.firstOrNull()?.type == "IHDR" && result.lastOrNull()?.type == "IEND" && cursor == bytes.size)
        return result
    }

    private fun metadata(chunk: Chunk): Pair<String, String>? = runCatching {
        if (chunk.type !in setOf("tEXt", "iTXt")) return null
        val d = chunk.data
        val keyEnd = d.indexOf(0)
        if (keyEnd < 1) return null
        val keyword = d.copyOfRange(0, keyEnd).toString(Charsets.US_ASCII)
        var pos = keyEnd + 1
        if (chunk.type == "tEXt") return keyword to d.copyOfRange(pos, d.size).toString(Charsets.UTF_8)
        require(pos + 2 <= d.size)
        val compressed = d[pos++].toInt(); val method = d[pos++].toInt()
        require(compressed in 0..1 && method == 0)
        repeat(2) { while (pos < d.size && d[pos] != 0.toByte()) pos++; require(pos < d.size); pos++ }
        val payload = d.copyOfRange(pos, d.size)
        val text = if (compressed == 1) InflaterInputStream(payload.inputStream()).use { input ->
            // Bound decompression to reject corrupt compressed metadata without allocating indefinitely.
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(out.size() + count <= 16 * 1024 * 1024)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } else payload
        keyword to text.toString(Charsets.UTF_8)
    }.getOrNull()

    private fun makeChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
        return ByteBuffer.allocate(data.size + 12).putInt(data.size).put(typeBytes).put(data).putInt(crc).array()
    }
}

private fun parseBook(obj: JsonObject): TavernEmbeddedBook = TavernEmbeddedBook(
    name = obj.string("name"), description = obj.string("description"),
    extensionsRaw = obj["extensions"]?.toString().orEmpty(), entries = obj["entries"].entryObjects().mapIndexed(::parseEntry),
)

/** Same aliases/precedence as the retained 花灯 importer, with the source enabled flag preserved. */
private fun parseEntry(index: Int, e: JsonObject): TavernBookEntry {
    val ext = e["extensions"] as? JsonObject ?: JsonObject(emptyMap())
    fun value(vararg keys: String): JsonElement? = keys.firstNotNullOfOrNull { ext[it] } ?: keys.firstNotNullOfOrNull { e[it] }
    fun int(default: Int, vararg keys: String) = value(*keys).integer() ?: default
    fun bool(vararg keys: String) = value(*keys).boolean()
    fun text(vararg keys: String) = (value(*keys) as? JsonPrimitive)?.contentOrNull.orEmpty()
    val position = value("position").let { (it as? JsonPrimitive)?.contentOrNull }.let { when (it) {
        "before_char", "before" -> 0; "after_char", "after" -> 1; else -> it?.toIntOrNull() ?: 1
    } }
    return TavernBookEntry(
        id = e.int("id", index), keys = (e["keys"].strings() + e["key"].strings()).distinct(),
        secondaryKeys = e["secondary_keys"].strings(), comment = e.string("comment"), content = e.string("content"),
        constant = bool("constant"), selective = bool("selective"), selectiveLogic = int(0, "selectiveLogic", "selective_logic"),
        group = text("group"), groupWeight = int(100, "group_weight", "groupWeight"),
        groupOverride = bool("group_override", "groupOverride", "group_priority"), position = position,
        priority = int(100, "order", "insertion_order", "priority"),
        disable = e["disable"]?.boolean() ?: (e["enabled"]?.boolean()?.not() ?: false),
        caseSensitive = bool("case_sensitive", "caseSensitive"), matchWholeWords = bool("match_whole_words", "matchWholeWords"),
        useRegex = bool("use_regex", "useRegex"), probability = int(100, "probability"),
        useProbability = value("use_probability", "useProbability")?.boolean() ?: (value("probability") != null),
        sticky = int(0, "sticky"), cooldown = int(0, "cooldown"), delay = int(0, "delay"), depth = int(4, "depth"),
        scanDepth = value("scan_depth", "scanDepth").integer(), role = roleOf(value("role")),
        excludeRecursion = bool("exclude_recursion", "excludeRecursion"), preventRecursion = bool("prevent_recursion", "preventRecursion"),
        delayUntilRecursion = int(0, "delay_until_recursion", "delayUntilRecursion"),
        ignoreBudget = bool("ignore_budget", "ignoreBudget"), outletName = text("outlet_name", "outletName"),
        matchPersonaDescription = bool("match_persona_description"), matchCharacterDescription = bool("match_character_description"),
        matchCharacterPersonality = bool("match_character_personality"), matchCharacterDepthPrompt = bool("match_character_depth_prompt"),
        matchScenario = bool("match_scenario"), matchCreatorNotes = bool("match_creator_notes"), extensionsRaw = ext.toString(),
    )
}

private fun TavernBookEntry.toInjection(raw: String): RegexInjection = RegexInjection(
    id = Uuid.fromLongs(0x0000000000004000L, Long.MIN_VALUE or (id.toLong() and 0xffffffffL)),
    name = comment.ifBlank { keys.firstOrNull().orEmpty() }, enabled = !disable, priority = priority,
    position = when (position) {
        0 -> InjectionPosition.BEFORE_SYSTEM_PROMPT
        1, 2 -> InjectionPosition.AFTER_SYSTEM_PROMPT
        3 -> InjectionPosition.BOTTOM_OF_CHAT
        5, 6 -> InjectionPosition.TOP_OF_CHAT
        else -> InjectionPosition.AT_DEPTH
    }, content = content, injectDepth = depth, role = MessageRole.valueOf(role.uppercase()),
    keywords = keys, useRegex = useRegex, caseSensitive = caseSensitive, scanDepth = scanDepth ?: 4, constantActive = constant,
    stPosition = position, stSecondaryKeys = secondaryKeys, stSelective = selective, stSelectiveLogic = selectiveLogic,
    stGroup = group, stGroupWeight = groupWeight, stGroupOverride = groupOverride, stProbability = probability,
    stUseProbability = useProbability, stSticky = sticky, stCooldown = cooldown, stDelay = delay,
    stDelayUntilRecursion = delayUntilRecursion, stExcludeRecursion = excludeRecursion, stPreventRecursion = preventRecursion,
    stMatchWholeWords = matchWholeWords, stIgnoreBudget = ignoreBudget, stOutletName = outletName, stComment = comment,
    stMatchPersonaDescription = matchPersonaDescription, stMatchCharacterDescription = matchCharacterDescription,
    stMatchCharacterPersonality = matchCharacterPersonality, stMatchCharacterDepthPrompt = matchCharacterDepthPrompt,
    stMatchScenario = matchScenario, stMatchCreatorNotes = matchCreatorNotes, stExtensionsRaw = extensionsRaw,
    stEntryRawJson = raw, stSource = "card",
)

private fun encodeEntry(e: RegexInjection, index: Int): JsonObject {
    val raw = objectOrEmpty(e.stEntryRawJson).toMutableMap()
    val ext = objectOrEmpty(e.stExtensionsRaw).toMutableMap()
    fun put(key: String, value: JsonElement) { ext[key] = value; if (key in raw) raw[key] = value }
    val position = e.stPosition.takeIf { it in 0..7 } ?: when (e.position.name) {
        "BEFORE_SYSTEM_PROMPT" -> 0; "AFTER_SYSTEM_PROMPT" -> 1; "TOP_OF_CHAT" -> 5; "BOTTOM_OF_CHAT" -> 3; else -> 4
    }
    put("position", JsonPrimitive(position)); put("role", JsonPrimitive(e.role.name.lowercase())); put("depth", JsonPrimitive(e.injectDepth))
    mapOf("selectiveLogic" to e.stSelectiveLogic, "group_weight" to e.stGroupWeight, "probability" to e.stProbability,
        "sticky" to e.stSticky, "cooldown" to e.stCooldown, "delay" to e.stDelay, "delay_until_recursion" to e.stDelayUntilRecursion,
        "scan_depth" to e.scanDepth, "order" to e.priority).forEach { (k, v) -> put(k, JsonPrimitive(v)) }
    mapOf("group_override" to e.stGroupOverride, "useProbability" to e.stUseProbability, "case_sensitive" to e.caseSensitive,
        "use_regex" to e.useRegex, "exclude_recursion" to e.stExcludeRecursion, "prevent_recursion" to e.stPreventRecursion,
        "match_whole_words" to e.stMatchWholeWords, "ignore_budget" to e.stIgnoreBudget,
        "match_persona_description" to e.stMatchPersonaDescription, "match_character_description" to e.stMatchCharacterDescription,
        "match_character_personality" to e.stMatchCharacterPersonality, "match_character_depth_prompt" to e.stMatchCharacterDepthPrompt,
        "match_scenario" to e.stMatchScenario, "match_creator_notes" to e.stMatchCreatorNotes).forEach { (k, v) -> put(k, JsonPrimitive(v)) }
    put("group", JsonPrimitive(e.stGroup)); put("outlet_name", JsonPrimitive(e.stOutletName))
    raw.putIfAbsent("id", JsonPrimitive(index)); raw["keys"] = e.keywords.jsonArray(); raw["secondary_keys"] = e.stSecondaryKeys.jsonArray()
    raw["comment"] = JsonPrimitive(if (e.stSource == "card") e.stComment else e.stComment.ifBlank { e.name }); raw["content"] = JsonPrimitive(e.content)
    raw["constant"] = JsonPrimitive(e.constantActive); raw["selective"] = JsonPrimitive(e.stSelective)
    raw["enabled"] = JsonPrimitive(e.enabled); if ("disable" in raw) raw["disable"] = JsonPrimitive(!e.enabled)
    raw["position"] = JsonPrimitive(if (position == 0) "before_char" else "after_char")
    raw["insertion_order"] = JsonPrimitive(e.priority); raw["extensions"] = JsonObject(ext)
    return JsonObject(raw)
}

private fun objectOrEmpty(text: String): JsonObject = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.int(key: String, default: Int) = this[key].integer() ?: default
private fun JsonElement?.integer(): Int? = (this as? JsonPrimitive)?.contentOrNull?.let { it.toIntOrNull() ?: when (it) { "true" -> 1; "false" -> 0; else -> null } }
private fun JsonElement?.boolean(): Boolean = when (this) {
    is JsonArray -> isNotEmpty()
    is JsonPrimitive -> contentOrNull?.let { it.equals("true", true) || (it.toIntOrNull() ?: 0) > 0 } ?: false
    else -> false
}
private fun JsonElement?.strings(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(contentOrNull?.takeIf { it.isNotBlank() })
    else -> emptyList()
}
private fun List<String>.jsonArray() = JsonArray(map(::JsonPrimitive))
private fun JsonElement?.entryObjects(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> entries.mapNotNull { (key, value) -> (value as? JsonObject)?.let {
        if ("id" in it) it else JsonObject(it + ("id" to JsonPrimitive(key.toIntOrNull() ?: 0)))
    } }
    else -> emptyList()
}
private fun roleOf(value: JsonElement?): String = when ((value as? JsonPrimitive)?.contentOrNull?.lowercase()) {
    "1", "user" -> "user"; "2", "assistant" -> "assistant"; else -> "system"
}
