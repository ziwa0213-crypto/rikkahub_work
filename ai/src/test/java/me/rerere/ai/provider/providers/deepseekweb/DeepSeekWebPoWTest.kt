package me.rerere.ai.provider.providers.deepseekweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.util.json
import java.util.Base64

class DeepSeekWebPoWTest {
    @Test
    fun `uses the requested target in upload and completion proofs`() {
        val challenge = buildJsonObject {
            put("algorithm", "DeepSeekHashV1")
            put("challenge", "challenge")
            put("salt", "salt")
            put("signature", "signature")
        }
        for (target in listOf("/api/v0/chat/completion", DeepSeekWebImages.UPLOAD_PATH)) {
            val payload = DeepSeekWebPoW.headerPayload(challenge, 123, target)
            val decoded = json.parseToJsonElement(String(Base64.getDecoder().decode(payload))) as kotlinx.serialization.json.JsonObject
            assertEquals(target, decoded["target_path"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `parses synchronous WebView answer`() {
        assertEquals(123, DeepSeekWebPoW.parseJavascriptResult("\"OK:123\""))
    }

    @Test
    fun `rejects Promise object returned by WebView`() {
        val error = assertThrows(IllegalStateException::class.java) {
            DeepSeekWebPoW.parseJavascriptResult("{}")
        }
        assertEquals(true, error.message?.contains("invalid result"))
    }

    @Test
    fun `surfaces JavaScript solver errors`() {
        val error = assertThrows(IllegalStateException::class.java) {
            DeepSeekWebPoW.parseJavascriptResult("\"ERR:PoW WASM exports missing\"")
        }
        assertEquals("PoW WASM exports missing", error.message)
    }
}
