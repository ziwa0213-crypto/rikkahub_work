package me.rerere.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class DeepSeekSearchServiceTest {
    private val options = SearchServiceOptions.DeepSeekOptions(apiKey = "secret-test-key", baseUrl = "https://example.test/")
    private val params = JsonObject(mapOf("query" to JsonPrimitive("ChatGPT")))

    @Test fun requestUsesOfficialContractAndHeaderOnlyKey() {
        val request = DeepSeekSearchService.buildRequest("ChatGPT", options, 5)
        val body = SearchService.json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals("https://example.test/v1/tools/search", request.url.toString())
        assertEquals("Bearer secret-test-key", request.header("Authorization"))
        assertEquals("ChatGPT", body["query"]!!.jsonPrimitive.content)
        assertEquals(5, body["count"]!!.jsonPrimitive.int)
        assertFalse(body["need_content"]!!.jsonPrimitive.boolean)
        assertEquals(setOf("query", "count", "need_content"), body.keys)
        assertFalse(request.url.toString().contains(options.apiKey))
    }

    @Test fun countIsClampedAtBothEnds() {
        for ((input, expected) in listOf(0 to 1, 100 to 20)) {
            val request = DeepSeekSearchService.buildRequest("test", options, input)
            assertTrue(Buffer().also { request.body!!.writeTo(it) }.readUtf8().contains("\"count\":$expected"))
        }
    }

    @Test fun longSummaryWinsAndSnippetDateArePreserved() {
        val result = DeepSeekSearchService.parseResponse("""{"results":[
            {"url":"https://example.test/1","summary":"long","snippet":"short","date":"2026-10-17T00:00:00Z","content":"never use"},
            {"url":"https://example.test/2","snippet":"fallback"},
            {"url":"https://example.test/3","summary":"  ","snippet":"blank fallback"},
            {"title":"missing URL"},{"url":" "}
        ]}""", 5)
        assertEquals(listOf("long", "fallback", "blank fallback"), result.items.map { it.text })
        assertEquals(listOf("short"), result.items.first().highlights)
        assertEquals("2026-10-17T00:00:00Z", result.items.first().publishedDate)
        assertEquals(1, DeepSeekSearchService.parseResponse("""{"results":[{"url":"a"},{"url":"b"}]}""", 1).items.size)
        assertTrue(DeepSeekSearchService.parseResponse("""{"results":[]}""", 5).items.isEmpty())
    }

    @Test fun fetchBuildsAndParsesMarkdown() {
        val request = DeepSeekSearchService.buildFetch("https://example.test/page", options)
        assertEquals("https://example.test/v1/tools/fetch", request.url.toString())
        assertEquals("Bearer secret-test-key", request.header("Authorization"))
        assertEquals("""{"url":"https://example.test/page"}""", Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        val item = DeepSeekSearchService.parseFetch("""{"title":"Page","content":"# Markdown"}""", "https://example.test/page").urls.single()
        assertEquals("# Markdown", item.content)
        assertEquals("Page", item.metadata?.title)
    }

    @Test fun emptyFetchAndInvalidInputsAreRejected() {
        assertThrows(IllegalStateException::class.java) { DeepSeekSearchService.parseFetch("""{"content":" "}""", "https://example.test") }
        for (url in listOf("file:///tmp/test", "https://user:pass@example.test", "https://example.test/" + "x".repeat(8192))) {
            assertThrows(IllegalStateException::class.java) { DeepSeekSearchService.buildFetch(url, options) }
        }
        for (query in listOf("", "x".repeat(129))) {
            assertThrows(IllegalStateException::class.java) { DeepSeekSearchService.buildRequest(query, options, 5) }
        }
    }

    @Test fun blankKeyNeverSendsSearchOrFetch() = runBlocking {
        withClient(200, "{}") { calls ->
            val empty = options.copy(apiKey = "  ")
            assertEquals("DeepSeek API key is required", DeepSeekSearchService.search(params, SearchCommonOptions(), empty).exceptionOrNull()?.message)
            assertEquals("DeepSeek API key is required", DeepSeekSearchService.scrape(JsonObject(mapOf("url" to JsonPrimitive("https://example.test"))), SearchCommonOptions(), empty).exceptionOrNull()?.message)
            assertEquals(0, calls.size)
        }
    }

    @Test fun globalLimitIsRespected() = runBlocking {
        withClient(200, """{"results":[]}""") { calls ->
            assertTrue(DeepSeekSearchService.search(params, SearchCommonOptions(2), options.copy(maxResults = 8)).isSuccess)
            assertTrue(Buffer().also { calls.single().body!!.writeTo(it) }.readUtf8().contains("\"count\":2"))
        }
    }

    @Test fun errorsAreActionableAndNeverIncludeResponseOrKey() = runBlocking {
        for ((code, text) in listOf(401 to "key is invalid", 402 to "balance is insufficient", 403 to "security policy", 404 to "usable page content", 429 to "5 QPS")) {
            withClient(code, "echo ${options.apiKey}") {
                val error = DeepSeekSearchService.search(params, SearchCommonOptions(), options).exceptionOrNull()!!
                assertTrue(error.message.orEmpty().contains(text))
                assertFalse(error.toString().contains(options.apiKey))
                assertNull(error.cause)
            }
        }
        withClient(200, """{"results":"${options.apiKey}"}""") {
            val error = DeepSeekSearchService.search(params, SearchCommonOptions(), options).exceptionOrNull()!!
            assertEquals("DeepSeek returned an invalid response", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun cancellationRemainsCancellation() = runBlocking {
        val original = SearchService.httpClient
        val started = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        try {
            SearchService.httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                started.countDown()
                check(finish.await(5, java.util.concurrent.TimeUnit.SECONDS))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body("""{"results":[]}""".toResponseBody()).build()
            }.build()
            val task = async(kotlinx.coroutines.Dispatchers.IO) { DeepSeekSearchService.search(params, SearchCommonOptions(), options) }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            task.cancel()
            finish.countDown()
            try {
                task.await()
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
        } finally { finish.countDown(); SearchService.httpClient = original }
    }

    @Test fun stableTypeDefaultAndScrapeCapability() {
        val decoded = SearchService.json.decodeFromString<SearchServiceOptions>("""{"type":"deepseek"}""") as SearchServiceOptions.DeepSeekOptions
        assertEquals(5, decoded.maxResults)
        assertEquals("https://api.deepseek.com", decoded.baseUrl)
        assertSame(DeepSeekSearchService, SearchService.getService(decoded))
        assertNotNull(DeepSeekSearchService.scrapingParameters(decoded))
        assertNull(SearchService.getService(SearchServiceOptions.DEFAULT).scrapingParameters(SearchServiceOptions.DEFAULT))
        assertTrue(SearchServiceOptions.DEFAULT is SearchServiceOptions.BingLocalOptions)
        assertTrue(SearchService.json.decodeFromString<SearchServiceOptions>("""{"type":"bing_local"}""") is SearchServiceOptions.BingLocalOptions)
    }

    private suspend fun withClient(code: Int, body: String, block: suspend (MutableList<okhttp3.Request>) -> Unit) {
        val original = SearchService.httpClient
        val calls = mutableListOf<okhttp3.Request>()
        try {
            SearchService.httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                calls.add(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
            }.build()
            block(calls)
        } finally { SearchService.httpClient = original }
    }
}
