package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebRequestParserTest {
    @Test
    fun parsesSingleQuotedAuthorization() {
        val parsed = DeepSeekWebRequestParser.parse("-H 'authorization: Bearer abc123'")
        assertEquals("abc123", parsed.token)
        assertTrue(parsed.authorizationFound)
    }

    @Test
    fun parsesDoubleQuotedAuthorization() {
        val parsed = DeepSeekWebRequestParser.parse("-H \"authorization: Bearer abc123\"")
        assertEquals("abc123", parsed.token)
        assertTrue(parsed.authorizationFound)
    }

    @Test
    fun parsesCookieWithoutAuthorization() {
        val parsed = DeepSeekWebRequestParser.parse("-H 'cookie: a=1; b=2'")
        assertEquals("a=1; b=2", parsed.cookie)
        assertEquals("", parsed.token)
        assertFalse(parsed.authorizationFound)
    }

    @Test
    fun parsesBareHeaders() {
        val parsed = DeepSeekWebRequestParser.parse("authorization: Bearer token\ncookie: sid=abc\nx-client: mobile")
        assertEquals("token", parsed.token)
        assertEquals("sid=abc", parsed.cookie)
        assertEquals("mobile", parsed.headers["x-client"])
    }

    @Test
    fun laterDuplicateHeaderWins() {
        val parsed = DeepSeekWebRequestParser.parse("-H 'cookie: first=1' -H 'cookie: last=2'")
        assertEquals("last=2", parsed.cookie)
    }

    @Test
    fun dataPayloadIsNotParsedAsHeader() {
        val parsed = DeepSeekWebRequestParser.parse("-H 'authorization: Bearer token' --data-raw '{\"chat_session_id\":\"id\"}'")
        assertEquals("token", parsed.token)
        assertFalse(parsed.headers.containsKey("chat_session_id"))
    }

    @Test
    fun powHeaderIsExcluded() {
        val parsed = DeepSeekWebRequestParser.parse("-H 'x-ds-pow-response: proof' -H 'x-client: mobile'")
        assertFalse(parsed.headers.containsKey("x-ds-pow-response"))
        assertEquals("mobile", parsed.headers["x-client"])
    }

    @Test
    fun headerNamesAreCaseInsensitive() {
        val parsed = DeepSeekWebRequestParser.parse("Authorization: Bearer token\nCOOKIE: sid=abc")
        assertEquals("token", parsed.token)
        assertEquals("sid=abc", parsed.cookie)
    }

    @Test
    fun blankAndUnrelatedInputReturnsEmptyResult() {
        for (input in listOf("", "   ", "not a request")) {
            val parsed = DeepSeekWebRequestParser.parse(input)
            assertTrue(parsed.isEmpty)
            assertEquals("", parsed.token)
            assertEquals("", parsed.cookie)
        }
    }

    @Test
    fun commandAndShellContinuationsAreNormalized() {
        val parsed = DeepSeekWebRequestParser.parse(
            "-H 'authorization: Bearer token' \\\n+                \n-H 'cookie: sid=abc' ^\n-H 'x-client: mobile'",
        )
        assertEquals("token", parsed.token)
        assertEquals("sid=abc", parsed.cookie)
        assertEquals("mobile", parsed.headers["x-client"])
    }

    @Test
    fun cookieFlagIsSupportedAndHeaderTakesPrecedence() {
        val parsed = DeepSeekWebRequestParser.parse(
            "-b 'ds_session_id=flag' -H 'cookie: ds_session_id=header'",
        )
        assertEquals("ds_session_id=header", parsed.cookie)
    }

    @Test
    fun completeHttpRequestIsParsed() {
        val parsed = DeepSeekWebRequestParser.parse(
            "POST /api/v0/chat/completion HTTP/1.1\nHost: chat.deepseek.com\nAuthorization: Bearer token\nCookie: sid=abc",
        )
        assertEquals("token", parsed.token)
        assertEquals("sid=abc", parsed.cookie)
        assertEquals("chat.deepseek.com", parsed.headers["host"])
    }

    @Test
    fun pseudoHeadersAreIgnored() {
        val parsed = DeepSeekWebRequestParser.parse(
            ":authority: chat.deepseek.com\n:method: POST\nauthorization: Bearer token",
        )
        assertEquals("token", parsed.token)
        assertFalse(parsed.headers.containsKey(":authority"))
        assertFalse(parsed.headers.containsKey(":method"))
    }
}
