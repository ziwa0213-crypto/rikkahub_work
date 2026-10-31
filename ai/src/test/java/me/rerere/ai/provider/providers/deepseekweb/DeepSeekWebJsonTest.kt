package me.rerere.ai.provider.providers.deepseekweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.Assert.assertThrows

class DeepSeekWebJsonTest {
    @Test
    fun `JsonNull data is treated as absent object`() {
        val root = parseDeepSeekResponse("""{"code":0,"data":null}""")

        assertNull(root["data"].objOrNull())
    }

    @Test
    fun `top level business error is readable`() {
        val error = assertThrows(IllegalStateException::class.java) {
            parseDeepSeekResponse("""{"code":40001,"msg":"未登录","data":null}""")
        }

        assertEquals("DeepSeek API error 40001: 未登录", error.message)
    }

    @Test
    fun `nested business error is readable`() {
        val error = assertThrows(IllegalStateException::class.java) {
            parseDeepSeekResponse("""{"code":0,"data":{"biz_code":40002,"biz_msg":"PoW 失败"}}""")
        }

        assertEquals("DeepSeek API error 40002: PoW 失败", error.message)
    }

    @Test
    fun `normal response remains available`() {
        val root = parseDeepSeekResponse("""{"code":0,"data":{"biz_data":{"id":"session-1"}}}""")

        assertEquals("session-1", root["data"].objOrNull()?.get("biz_data").objOrNull()?.get("id")?.toString()?.trim('"'))
    }

    @Test
    fun `malformed response does not echo credentials`() {
        val error = assertThrows(IllegalStateException::class.java) {
            parseDeepSeekResponse("not-json-cookie=secret-token")
        }

        assertFalse(error.message.orEmpty().contains("secret-token"))
        assertFalse(error.message.orEmpty().contains("cookie"))
    }

    @Test
    fun `all non object response roots fail without echoing content`() {
        for (body in listOf("null", "[]", "42", "\"test-secret\"")) {
            val error = assertThrows(IllegalStateException::class.java) {
                parseDeepSeekResponse(body)
            }
            assertEquals("DeepSeek response is not a JSON object", error.message)
        }
    }

    @Test
    fun `null and array nested values do not throw object cast errors`() {
        for (value in listOf("null", "[]")) {
            val root = parseDeepSeekResponse("""{"code":0,"data":{"biz_data":$value}}""")
            assertNull(root["data"].objOrNull()?.get("biz_data").objOrNull())
        }
    }

    @Test
    fun `string codes and blank message fallback are supported`() {
        parseDeepSeekResponse("""{"code":"0","data":{"biz_code":"0"}}""")
        val error = assertThrows(IllegalStateException::class.java) {
            parseDeepSeekResponse("""{"code":"40001","msg":"","message":"未登录"}""")
        }
        assertEquals("DeepSeek API error 40001: 未登录", error.message)
    }

    @Test
    fun `error messages omit complete credential details`() {
        for (message in listOf(
            "Cookie: session_id=test-cookie-one; other=test-cookie-two",
            "Authorization: Bearer test-bearer-value",
            "token=test-token-value",
            "api_key: test-api-key",
            "test1234567890abcdefghijklmnopqrstuvwxyz1234567890",
        )) {
            val body = buildJsonObject {
                put("code", 40001)
                put("msg", message)
                put("token", "test-top-level-secret")
            }.toString()
            val error = assertThrows(IllegalStateException::class.java) { parseDeepSeekResponse(body) }
            assertEquals("DeepSeek API error 40001: request failed (sensitive details omitted)", error.message)
        }
    }

    @Test
    fun `malformed error codes do not leak their value`() {
        val error = assertThrows(IllegalStateException::class.java) {
            parseDeepSeekResponse("""{"code":"test-secret-code","msg":"ignored"}""")
        }
        assertEquals("DeepSeek response has an invalid error code", error.message)
    }
}
