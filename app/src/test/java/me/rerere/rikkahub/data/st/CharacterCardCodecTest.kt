package me.rerere.rikkahub.data.st

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.`import`.CharacterCardCodec
import me.rerere.rikkahub.ui.pages.character.editFirstText
import me.rerere.rikkahub.ui.pages.character.parseRegexScriptsJson
import org.junit.Assert.*
import org.junit.Test

class CharacterCardCodecTest {
    private val original = """{"spec":"chara_card_v3","spec_version":"3.0","custom_top":{"x":[3,true,null,"end"]},"data":{"name":"Ada","description":"d","first_mes":"hi","alternate_greetings":["a","b"],"assets":[{"type":"image","uri":"embed:a","unknown":17}],"source":["b","a"],"creator":"Maker","group_only_greetings":["g"],"unknown":{"array":[{"z":false},2]},"extensions":{"unknown":{"v":null},"depth_prompt":{"prompt":"depth","depth":8,"role":0,"extra":"keep"},"regex_scripts":[{"id":"s","scriptName":"s","findRegex":"x","placement":[2],"replaceString":"y","markdownOnly":true}]},"character_book":{"name":"book","custom_book":[3,2,1],"entries":[{"id":42,"keys":["key"],"secondary_keys":["s"],"content":"lore","constant":false,"enabled":true,"position":"after_char","order":88,"unknown":["keep",false],"extensions":{"position":4,"role":0,"depth":6,"ignore_budget":true,"probability":51,"useProbability":true,"selectiveLogic":3,"group":"g","group_override":true,"group_weight":17,"sticky":2,"cooldown":3,"delay":4,"delay_until_recursion":true,"exclude_recursion":true,"prevent_recursion":true,"match_whole_words":true,"match_persona_description":true,"match_character_description":true,"match_character_personality":true,"match_character_depth_prompt":true,"match_scenario":true,"match_creator_notes":true,"outlet_name":"o","mystery":{"a":[2,1]}}}]}}}"""

    @Test fun v3RoundTripPreservesUnknownValuesAndArrayOrder() {
        val parsed = requireNotNull(CharacterCardCodec.parse(original.toByteArray(), "a.json"))
        val (character, books) = parsed.materialize()
        val result = Json.parseToJsonElement(CharacterCardCodec.encodeJson(character.copy(description = "edited"), books)).jsonObject
        val source = Json.parseToJsonElement(original).jsonObject
        assertEquals(source["custom_top"], result["custom_top"])
        for (key in listOf("assets", "source", "creator", "group_only_greetings", "unknown")) assertEquals(source["data"]!!.jsonObject[key], result["data"]!!.jsonObject[key])
        val book = result["data"]!!.jsonObject["character_book"]!!.jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(2), JsonPrimitive(1))), book["custom_book"])
        val entry = book["entries"]!!.jsonArray.single().jsonObject
        assertEquals(Json.parseToJsonElement("[\"keep\",false]"), entry["unknown"])
        assertEquals(Json.parseToJsonElement("{\"a\":[2,1]}"), entry["extensions"]!!.jsonObject["mystery"])
        assertEquals("edited", result["data"]!!.jsonObject["description"]!!.jsonPrimitive.content)
        assertEquals("keep", result["data"]!!.jsonObject["extensions"]!!.jsonObject["depth_prompt"]!!.jsonObject["extra"]!!.jsonPrimitive.content)
        assertEquals(1, character.stRegexScripts.size)
    }

    @Test fun allWorldbookFieldsMapAndReparse() {
        val (c, books) = requireNotNull(CharacterCardCodec.parse(original.toByteArray(), "a.json")).materialize()
        val entry = books.single().entries.single()
        assertTrue(entry.enabled); assertEquals(88, entry.priority); assertEquals(MessageRole.SYSTEM, entry.role)
        assertEquals(4, entry.stPosition); assertEquals(6, entry.injectDepth); assertEquals(1, entry.stDelayUntilRecursion)
        assertEquals(17, entry.stGroupWeight); assertTrue(entry.stGroupOverride); assertTrue(entry.stIgnoreBudget)
        assertTrue(entry.stMatchCreatorNotes); assertTrue(entry.stMatchCharacterDepthPrompt); assertTrue(entry.stMatchWholeWords)
        assertTrue(entry.stExcludeRecursion); assertTrue(entry.stPreventRecursion); assertEquals("o", entry.stOutletName)
        val next = requireNotNull(CharacterCardCodec.parse(CharacterCardCodec.encodeJson(c, books).toByteArray(), "b.json")).materialize().second.single().entries.single()
        assertEquals(entry.copy(stEntryRawJson = "", stExtensionsRaw = ""), next.copy(stEntryRawJson = "", stExtensionsRaw = ""))
    }

    @Test fun oversizedCardWarnsAndDiscardsOriginalSnapshot() {
        val big = original.replace("\"custom_top\":", "\"huge\":\"${"x".repeat(CharacterCardCodec.RAW_LIMIT_BYTES)}\",\"custom_top\":")
        val parsed = requireNotNull(CharacterCardCodec.parse(big.toByteArray(), "a.json"))
        assertTrue(parsed.rawCardTruncated); assertEquals("", parsed.rawCardJson)
        assertTrue(parsed.materialize().first.rawCardTruncated)
        assertFalse(CharacterCardCodec.encodeJson(parsed.materialize().first).contains("\"huge\""))
    }

    @Test fun pngPrefersV3AndPreservesImageChunksExactly() {
        val v2 = original.replace("chara_card_v3", "chara_card_v2").replace("\"Ada\"", "\"Old\"")
        val idat = chunk("IDAT", byteArrayOf(9, 5, 1, 2, 3))
        val png = png(chunk("tEXt", "chara\u0000".toByteArray() + Base64.getEncoder().encode(v2.toByteArray())),
            chunk("iTXt", international(original, true)), idat)
        val parsed = requireNotNull(CharacterCardCodec.parse(png, "c.png"))
        assertEquals("Ada", parsed.card.name); assertArrayEquals(png, parsed.imageBytes)
        val (character, books) = parsed.materialize()
        val encoded = CharacterCardCodec.encodePng(parsed.imageBytes, character.copy(name = "Changed"), books)
        assertTrue(encoded.toList().windowed(idat.size).any { it == idat.toList() })
        assertEquals("Changed", requireNotNull(CharacterCardCodec.parse(encoded, "c.png")).card.name)
        val nonCompressed = png(chunk("iTXt", international(original, false)))
        assertNotNull(CharacterCardCodec.parse(nonCompressed, "c.png"))
        val invalidV3 = chunk("tEXt", "ccv3\u0000".toByteArray() + Base64.getEncoder().encode("""{"spec":"other","data":{"name":"Invalid"}}""".toByteArray()))
        val validV2 = chunk("tEXt", "chara\u0000".toByteArray() + Base64.getEncoder().encode(v2.toByteArray()))
        assertEquals("Old", requireNotNull(CharacterCardCodec.parse(png(invalidV3, validV2), "fallback.png")).card.name)
    }

    @Test fun disabledAndObjectEntryShapesArePreserved() {
        val data = """{"spec":"chara_card_v2","data":{"name":"n","character_book":{"entries":{"9":{"keys":["k"],"content":"c","disable":true,"extensions":{"role":2}}}}}}"""
        val (c, books) = requireNotNull(CharacterCardCodec.parse(data.toByteArray(), "c.json")).materialize()
        assertFalse(books.single().entries.single().enabled)
        assertEquals(MessageRole.ASSISTANT, books.single().entries.single().role)
        val output = Json.parseToJsonElement(CharacterCardCodec.encodeJson(c, books)).jsonObject
        assertTrue(output["data"]!!.jsonObject["character_book"]!!.jsonObject["entries"] is JsonObject)
    }

    @Test fun regexUnknownMetadataSurvivesAnEdit() {
        val input = original.replace("\"id\":\"s\",\"scriptName\"", "\"extra_regex\":{\"values\":[3,false,null]},\"id\":\"s\",\"scriptName\"")
        val (character, books) = requireNotNull(CharacterCardCodec.parse(input.toByteArray(), "c.json")).materialize()
        val edited = character.copy(stRegexScripts = character.stRegexScripts.map { it.copy(replaceString = "new") })
        val root = Json.parseToJsonElement(CharacterCardCodec.encodeJson(edited, books)).jsonObject
        val script = root["data"]!!.jsonObject["extensions"]!!.jsonObject["regex_scripts"]!!.jsonArray.single().jsonObject
        assertEquals(Json.parseToJsonElement("""{"values":[3,false,null]}"""), script["extra_regex"])
        assertEquals("new", script["replaceString"]!!.jsonPrimitive.content)
    }

    @Test fun malformedAndNonCardsReturnNull() {
        assertNull(CharacterCardCodec.parse("{}".toByteArray(), "a.json"))
        assertNull(CharacterCardCodec.parse(original.toByteArray(), "a.txt"))
        assertNull(CharacterCardCodec.parse(byteArrayOf(137.toByte(), 80, 78, 71), "a.png"))
        val bytes = png(chunk("tEXt", "chara\u0000INVALID".toByteArray()))
        assertNull(CharacterCardCodec.parse(bytes, "a.png"))
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        assertNull(CharacterCardCodec.parse(bytes, "a.png"))
    }

    @Test fun presetEditAndRegexImportPreserveOtherData() {
        val first = UIMessage.user("first").copy(parts = listOf(UIMessagePart.Text("first"), UIMessagePart.Text("tail")))
        val second = UIMessage.assistant("keep")
        val result = listOf(first, second).editFirstText("changed")
        assertEquals(first.role, result.first().role); assertEquals(first.id, result.first().id)
        assertEquals(first.parts[1], result.first().parts[1]); assertEquals(second, result[1])
        assertEquals(2, parseRegexScriptsJson("""[{"findRegex":"a","placement":[1]},{"find_regex":"b","placement":[2]}]""").size)
    }

    private fun international(text: String, compressed: Boolean): ByteArray {
        val base = Base64.getEncoder().encode(text.toByteArray())
        val payload = if (!compressed) base else ByteArrayOutputStream().apply { DeflaterOutputStream(this).use { it.write(base) } }.toByteArray()
        return "ccv3\u0000".toByteArray() + byteArrayOf(if (compressed) 1 else 0, 0, 0, 0) + payload
    }
    private fun png(vararg metadata: ByteArray): ByteArray = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) +
        chunk("IHDR", ByteArray(13)) + metadata.fold(byteArrayOf()) { a, b -> a + b } + chunk("IEND", byteArrayOf())
    private fun chunk(type: String, payload: ByteArray): ByteArray {
        val t = type.toByteArray(); val crc = CRC32().apply { update(t); update(payload) }.value.toInt()
        return ByteBuffer.allocate(payload.size + 12).putInt(payload.size).put(t).put(payload).putInt(crc).array()
    }
}
