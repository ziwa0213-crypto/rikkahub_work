package me.rerere.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class DeepSeekSearchServiceTest {
    private val options = SearchServiceOptions.DeepSeekOptions(apiKey = "secret-test-key")
    private val params = JsonObject(mapOf("query" to JsonPrimitive("ChatGPT")))

    @Test fun requestUsesUpstreamOfficialContractAndHeaderOnlyKey() = runBlocking {
        withClient(200, """{"results":[]}""") { calls ->
            assertTrue(DeepSeekSearchService.search(params, SearchCommonOptions(5), options).isSuccess)
            val request = calls.single()
            val body = requestBody(request)
            assertEquals("https://api.deepseek.com/v1/tools/search", request.url.toString())
            assertEquals("Bearer secret-test-key", request.header("Authorization"))
            assertEquals("ChatGPT", body["query"]!!.jsonPrimitive.content)
            assertEquals(5, body["count"]!!.jsonPrimitive.int)
            assertEquals(setOf("query", "count"), body.keys)
            assertFalse(request.url.toString().contains(options.apiKey))
        }
    }

    @Test fun commonResultLimitIsClampedToUpstreamRange() = runBlocking {
        for ((input, expected) in listOf(0 to 1, 2 to 2, 100 to 50)) {
            withClient(200, """{"results":[]}""") { calls ->
                assertTrue(DeepSeekSearchService.search(params, SearchCommonOptions(input), options.copy(maxResults = 8)).isSuccess)
                assertEquals(expected, requestBody(calls.single())["count"]!!.jsonPrimitive.int)
            }
        }
    }

    @Test fun summarySnippetDateAndEmptySnippetsFollowUpstream() = runBlocking {
        withClient(200, """{"results":[
            {"title":"One","url":"https://example.test/1","summary":"long","snippet":"short","date":"2026-10-17","content":"not used"},
            {"title":"Two","url":"https://example.test/2","snippet":"fallback"},
            {"title":"Three","url":"https://example.test/3","summary":"  ","snippet":"blank fallback"},
            {"title":"Four","url":"https://example.test/4"}
        ]}""") {
            val result = DeepSeekSearchService.search(params, SearchCommonOptions(), options).getOrThrow()
            assertEquals(listOf("long", "fallback", "blank fallback", ""), result.items.map { it.text })
            assertEquals("2026-10-17", result.items.first().publishedDate)
        }
    }

    @Test fun fetchUsesOfficialEndpointAndParsesMarkdown() = runBlocking {
        withClient(200, """{"title":"Page","content":"# Markdown"}""") { calls ->
            val result = DeepSeekSearchService.scrape(
                JsonObject(mapOf("url" to JsonPrimitive("https://example.test/page"))), SearchCommonOptions(), options
            ).getOrThrow().urls.single()
            val request = calls.single()
            assertEquals("https://api.deepseek.com/v1/tools/fetch", request.url.toString())
            assertEquals("Bearer secret-test-key", request.header("Authorization"))
            assertEquals("https://example.test/page", requestBody(request)["url"]!!.jsonPrimitive.content)
            assertEquals("# Markdown", result.content)
            assertEquals("Page", result.metadata?.title)
        }
    }

    @Test fun blankKeyNeverSendsSearchOrFetch() = runBlocking {
        withClient(200, "{}") { calls ->
            val empty = options.copy(apiKey = "  ")
            assertEquals("DeepSeek API key is required", DeepSeekSearchService.search(params, SearchCommonOptions(), empty).exceptionOrNull()?.message)
            assertEquals("DeepSeek API key is required", DeepSeekSearchService.scrape(JsonObject(mapOf("url" to JsonPrimitive("https://example.test"))), SearchCommonOptions(), empty).exceptionOrNull()?.message)
            assertTrue(calls.isEmpty())
        }
    }

    @Test fun errorsAndMalformedResponsesCannotExposeKeys() = runBlocking {
        for ((code, body) in listOf(
            401 to "echo secret-test-key",
            429 to """{"error":{"message":"rate limit secret-test-key"}}""",
            200 to """{"results":"secret-test-key"}"""
        )) {
            withClient(code, body) {
                val error = DeepSeekSearchService.search(params, SearchCommonOptions(), options).exceptionOrNull()!!
                assertFalse(error.toString().contains(options.apiKey))
                assertNull(error.cause)
                if (code != 200) assertTrue(error.message.orEmpty().contains("#$code"))
            }
        }
    }

    @Test fun stableTypeLegacyFieldsAndAdditionalEnginesSurvive() {
        val decoded = SearchService.json.decodeFromString<SearchServiceOptions>("""{"type":"deepseek","baseUrl":"https://legacy.test","maxResults":7}""") as SearchServiceOptions.DeepSeekOptions
        assertEquals(7, decoded.maxResults)
        assertEquals("https://legacy.test", decoded.baseUrl)
        assertSame(DeepSeekSearchService, SearchService.getService(decoded))
        assertNotNull(DeepSeekSearchService.scrapingParameters(decoded))
        assertTrue(SearchServiceOptions.DEFAULT is SearchServiceOptions.BingLocalOptions)
        assertTrue(SearchServiceOptions.DeepSeekOptions::class in SearchServiceOptions.TYPES)
        assertTrue(SearchServiceOptions.AnySearchOptions::class in SearchServiceOptions.TYPES)
        assertTrue(SearchServiceOptions.SuiXiangOptions::class in SearchServiceOptions.TYPES)
    }

    private fun requestBody(request: Request) = SearchService.json.parseToJsonElement(
        Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    ).jsonObject

    private suspend fun withClient(code: Int, body: String, block: suspend (MutableList<Request>) -> Unit) {
        val original = SearchService.httpClient
        val calls = mutableListOf<Request>()
        try {
            // Intercept before DNS/socket access: this suite never contacts an actual provider.
            SearchService.httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                calls.add(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                    .message("fixture").body(body.toResponseBody()).build()
            }.build()
            block(calls)
        } finally { SearchService.httpClient = original }
    }
}
