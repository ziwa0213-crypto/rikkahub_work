package me.rerere.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiXiangSearchServiceTest {
    @Test
    fun requestUsesQueryAndWebEndpoint() {
        val request = SuiXiangSearchService.buildRequest(
            query = "ChatGPT",
            options = SearchServiceOptions.SuiXiangOptions(
                apiKey = "test-key",
                baseUrl = "https://example.test/",
            ),
            resultSize = 5,
        )
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

        assertEquals("https://example.test/v1/web_search", request.url.toString())
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertTrue(body.contains("\"query\":\"ChatGPT\""))
        assertFalse(body.contains("\"input\""))
        assertTrue(body.contains("\"max_results\":5"))
    }

    @Test
    fun requestClampsResultSizeToTwenty() {
        val request = SuiXiangSearchService.buildRequest(
            query = "RikkaHub",
            options = SearchServiceOptions.SuiXiangOptions(apiKey = "test-key"),
            resultSize = 100,
        )
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

        assertTrue(body.contains("\"max_results\":20"))
    }

    @Test
    fun parseResponseKeepsEmptySnippetAndDropsMissingUrl() {
        val result = SuiXiangSearchService.parseResponse(
            """
            {
              "results": [
                {"url":"https://x.com/example","title":"Post","snippet":""},
                {"title":"Missing URL","snippet":"ignored"}
              ]
            }
            """.trimIndent(),
            maxResults = 10,
        )

        assertEquals(1, result.items.size)
        assertEquals("https://x.com/example", result.items.single().url)
        assertEquals("", result.items.single().text)
    }

    @Test
    fun parseResponseHonorsMaxResults() {
        val result = SuiXiangSearchService.parseResponse(
            """
            {"results":[
              {"url":"https://example.test/1","title":"One"},
              {"url":"https://example.test/2","title":"Two"}
            ]}
            """.trimIndent(),
            maxResults = 1,
        )

        assertEquals(1, result.items.size)
        assertEquals("One", result.items.single().title)
    }

    @Test
    fun missingApiKeyFailsWithoutSendingARequest() = runBlocking {
        val result = SuiXiangSearchService.search(
            params = JsonObject(mapOf("query" to JsonPrimitive("test"))),
            commonOptions = SearchCommonOptions(),
            serviceOptions = SearchServiceOptions.SuiXiangOptions(),
        )

        assertEquals("API key is required", result.exceptionOrNull()?.message)
    }

    @Test
    fun requestDoesNotExposeApiKeyInUrl() {
        val options = SearchServiceOptions.SuiXiangOptions(apiKey = "secret-key")
        val request = SuiXiangSearchService.buildRequest(
            query = "test",
            options = options,
            resultSize = 1,
        )

        assertFalse(request.url.toString().contains(options.apiKey))
    }

    @Test
    fun legacySuixiangJsonDecodesAsWebSearchAndIgnoresMode() {
        val options = SearchService.json.decodeFromString<List<SearchServiceOptions>>(
            """
            [{
              "type":"suixiang",
              "id":"00000000-0000-0000-0000-000000000001",
              "apiKey":"test-key",
              "mode":"x",
              "baseUrl":"https://example.test",
              "maxResults":10
            }]
            """.trimIndent(),
        ).single()

        assertTrue(options is SearchServiceOptions.SuiXiangOptions)
        assertEquals("随想搜索", options.displayName)
    }

    @Test
    fun legacySuiXiangJsonIgnoresRemovedMode() {
        val options = SearchService.json.decodeFromString<SearchServiceOptions>(
            """
            {
              "type":"suixiang",
              "id":"00000000-0000-0000-0000-000000000002",
              "apiKey":"test-key",
              "baseUrl":"https://example.test",
              "mode":"web",
              "maxResults":10
            }
            """.trimIndent(),
        ) as SearchServiceOptions.SuiXiangOptions

        assertEquals(10, options.maxResults)
    }

    @Test
    fun newSuiXiangOptionsDefaultToFiveResults() {
        val options = SearchServiceOptions.SuiXiangOptions()

        assertEquals(5, options.maxResults)
    }
}
