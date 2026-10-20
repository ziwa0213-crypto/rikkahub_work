package me.rerere.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AnySearchServiceTest {
    private val key = "secret-test-key"
    private val params = JsonObject(mapOf("query" to JsonPrimitive("ChatGPT")))

    @Test fun requestsUseOfficialPathsAndOptionalHeaderOnlyKey() {
        for (apiKey in listOf("", "  ", key)) {
            val request = AnySearchService.buildRequest("ChatGPT", 10, apiKey)
            val extract = AnySearchService.buildExtract("https://example.test/page", apiKey)
            assertEquals("https://api.anysearch.com/v1/search", request.url.toString())
            assertEquals("https://api.anysearch.com/v1/extract", extract.url.toString())
            for (r in listOf(request, extract)) {
                if (apiKey.isBlank()) assertNull(r.header("Authorization"))
                else assertEquals("Bearer $key", r.header("Authorization"))
                assertFalse(r.url.toString().contains(key))
            }
            assertEquals("""{"query":"ChatGPT","max_results":10}""", body(request))
            assertEquals("""{"url":"https://example.test/page"}""", body(extract))
        }
    }

    @Test fun resultCountClampsToOneThroughTen() {
        assertTrue(body(AnySearchService.buildRequest("q", 0)).contains("\"max_results\":1"))
        assertTrue(body(AnySearchService.buildRequest("q", 100)).contains("\"max_results\":10"))
    }

    @Test fun parserPrefersLongerTextAndDropsOnlyMissingUrls() {
        val result = AnySearchService.parseResponse("""{"code":0,"data":{"results":[
            {"title":"One","url":"https://example.test/1","snippet":"short","content":"longer content"},
            {"url":"https://example.test/2","snippet":"longer snippet","content":"short"},
            {"url":"https://example.test/3","snippet":"","content":""},
            {"title":"missing URL"},{"url":" "}
        ]}}""", 10)
        assertEquals(listOf("longer content", "longer snippet", ""), result.items.map { it.text })
        assertEquals("One", result.items.first().title)
        assertTrue(AnySearchService.parseResponse("""{"code":0,"data":{"results":[]}}""", 10).items.isEmpty())
        assertEquals(1, AnySearchService.parseResponse("""{"code":0,"data":{"results":[{"url":"a"},{"url":"b"}]}}""", 1).items.size)
    }

    @Test fun businessErrorIncludesSafeMessageAndRejectsMissingCode() {
        val error = assertThrows(IllegalStateException::class.java) {
            AnySearchService.parseResponse("""{"code":42,"message":"quota for $key"}""", 10, key)
        }
        assertTrue(error.message.orEmpty().contains("quota for [redacted]"))
        assertFalse(error.toString().contains(key))
        assertThrows(IllegalStateException::class.java) { AnySearchService.parseResponse("{}", 10) }
    }

    @Test fun extractMapsTitleAndMarkdownAndRejectsEmptyContent() {
        val item = AnySearchService.parseExtract("""{"code":0,"data":{"title":"Page","content":"# Markdown"}}""", "https://example.test/page").urls.single()
        assertEquals("Page", item.metadata?.title)
        assertEquals("# Markdown", item.content)
        assertEquals("https://example.test/page", item.url)
        assertThrows(IllegalStateException::class.java) { AnySearchService.parseExtract("""{"code":0,"data":{"content":" "}}""", "https://example.test") }
        assertThrows(IllegalStateException::class.java) { AnySearchService.parseExtract("""{"code":9,"message":"blocked"}""", "https://example.test") }
    }

    @Test fun globalLimitAndAnonymousExecutionWorkWithoutNetwork() = runBlocking {
        withClient(200, """{"code":0,"data":{"results":[]}}""") { calls ->
            assertTrue(AnySearchService.search(params, SearchCommonOptions(3), SearchServiceOptions.AnySearchOptions(maxResults = 8)).isSuccess)
            assertNull(calls.single().header("Authorization"))
            assertTrue(body(calls.single()).contains("\"max_results\":3"))
        }
    }

    @Test fun invalidKeyIsNotRetriedAsAnonymousAndErrorsNeverLeakIt() = runBlocking {
        for (code in listOf(401, 403, 429, 500)) {
            withClient(code, "echo $key") { calls ->
                val error = AnySearchService.search(params, SearchCommonOptions(), SearchServiceOptions.AnySearchOptions(apiKey = key)).exceptionOrNull()!!
                assertFalse(error.toString().contains(key))
                assertNull(error.cause)
                assertEquals(1, calls.size)
                assertEquals("Bearer $key", calls.single().header("Authorization"))
                if (code == 401) assertTrue(error.message.orEmpty().contains("Remove the key"))
            }
        }
        withClient(200, """{"code":5,"message":"failed $key"}""") {
            assertFalse(AnySearchService.search(params, SearchCommonOptions(), SearchServiceOptions.AnySearchOptions(apiKey = key)).exceptionOrNull().toString().contains(key))
        }
        withClient(200, """{"code":"$key"}""") {
            val error = AnySearchService.search(params, SearchCommonOptions(), SearchServiceOptions.AnySearchOptions(apiKey = key)).exceptionOrNull()!!
            assertEquals("AnySearch returned an invalid response", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun invalidParamsFailBeforeSending() = runBlocking {
        withClient(200, "{}") { calls ->
            assertTrue(AnySearchService.search(JsonObject(emptyMap()), SearchCommonOptions(), SearchServiceOptions.AnySearchOptions()).isFailure)
            assertTrue(AnySearchService.scrape(JsonObject(mapOf("url" to JsonPrimitive("file:///tmp/test"))), SearchCommonOptions(), SearchServiceOptions.AnySearchOptions()).isFailure)
            assertTrue(calls.isEmpty())
        }
    }

    @Test fun stableSerializationAndScrapingCapability() {
        val options = SearchService.json.decodeFromString<SearchServiceOptions>("""{"type":"anysearch"}""") as SearchServiceOptions.AnySearchOptions
        assertEquals(10, options.maxResults)
        assertEquals("", options.apiKey)
        assertSame(AnySearchService, SearchService.getService(options))
        assertNotNull(AnySearchService.scrapingParameters(options))
        assertEquals(listOf("DeepSeek", "AnySearch"), SearchServiceOptions.TYPES.values.toList().takeLast(2))
    }

    private fun body(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    private suspend fun withClient(code: Int, body: String, block: suspend (MutableList<Request>) -> Unit) {
        val original = SearchService.httpClient
        val calls = mutableListOf<Request>()
        try {
            SearchService.httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                calls.add(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
            }.build()
            block(calls)
        } finally { SearchService.httpClient = original }
    }
}
