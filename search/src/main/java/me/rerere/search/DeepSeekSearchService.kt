package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_RESULTS = 20
private const val MIN_INTERVAL_MS = 250L

object DeepSeekSearchService : SearchService<SearchServiceOptions.DeepSeekOptions> {
    override val name = "DeepSeek"
    private val pacing = Mutex()
    private var lastStartNanos: Long? = null

    @Composable
    override fun Description() {
        Text(stringResource(R.string.search_deepseek_description))
    }

    override fun parameters(options: SearchServiceOptions.DeepSeekOptions) = InputSchema.Obj(
        properties = buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "Search keyword (1 to 128 characters)")
            })
        },
        required = listOf("query"),
    )

    override fun scrapingParameters(options: SearchServiceOptions.DeepSeekOptions) = InputSchema.Obj(
        properties = buildJsonObject {
            put("url", buildJsonObject {
                put("type", "string")
                put("description", "HTTP or HTTPS page URL to fetch")
            })
        },
        required = listOf("url"),
    )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DeepSeekOptions,
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        safeResult {
            requireKey(serviceOptions.apiKey)
            val query = stringParam(params, "query")
            val size = minOf(serviceOptions.maxResults.coerceIn(1, MAX_RESULTS),
                commonOptions.resultSize.coerceIn(1, MAX_RESULTS))
            val request = buildRequest(query, serviceOptions, size)
            pace()
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) failWith("search", response.code)
                parseResponse(response.body.string(), size)
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DeepSeekOptions,
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        safeResult {
            requireKey(serviceOptions.apiKey)
            val url = stringParam(params, "url")
            val request = buildFetch(url, serviceOptions)
            pace()
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) failWith("fetch", response.code)
                parseFetch(response.body.string(), url)
            }
        }
    }

    internal fun buildRequest(
        query: String,
        options: SearchServiceOptions.DeepSeekOptions,
        resultSize: Int,
        apiKey: String = options.apiKey.trim(),
    ): Request {
        if (query.isBlank() || query.length > 128) fail("DeepSeek query must contain 1 to 128 characters")
        return request(options, apiKey, "/v1/tools/search", buildJsonObject {
            put("query", query)
            put("count", resultSize.coerceIn(1, MAX_RESULTS))
            put("need_content", false)
        })
    }

    internal fun buildFetch(
        url: String,
        options: SearchServiceOptions.DeepSeekOptions,
        apiKey: String = options.apiKey.trim(),
    ): Request {
        val parsed = url.toHttpUrlOrNull()
        if (url.length > 8192 || parsed == null || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            fail("DeepSeek fetch requires an HTTP(S) URL without credentials (up to 8192 characters)")
        }
        return request(options, apiKey, "/v1/tools/fetch", buildJsonObject { put("url", url) })
    }

    private fun request(options: SearchServiceOptions.DeepSeekOptions, apiKey: String, path: String, body: JsonObject): Request {
        val key = requireKey(apiKey)
        val endpoint = (options.baseUrl.trim().trimEnd('/') + path).toHttpUrlOrNull()
        if (endpoint == null || endpoint.username.isNotEmpty() || endpoint.password.isNotEmpty() ||
            endpoint.query != null || endpoint.fragment != null || endpoint.toString().contains(key)) {
            fail("DeepSeek service URL must be an HTTP(S) URL without credentials or query parameters")
        }
        return Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    internal fun parseResponse(responseBody: String, maxResults: Int): SearchResult {
        val response = decode<DeepSeekSearchResponse>(responseBody)
        return SearchResult(items = response.results.mapNotNull { item ->
            val url = item.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            SearchResult.SearchResultItem(
                title = item.title.orEmpty(), url = url,
                text = item.summary?.takeIf(String::isNotBlank) ?: item.snippet.orEmpty(),
                publishedDate = item.date?.takeIf(String::isNotBlank),
                highlights = listOfNotNull(item.snippet?.takeIf(String::isNotBlank)),
            )
        }.take(maxResults.coerceIn(1, MAX_RESULTS)))
    }

    internal fun parseFetch(responseBody: String, url: String): ScrapedResult {
        val response = decode<DeepSeekFetchResponse>(responseBody)
        val content = response.content?.takeIf(String::isNotBlank) ?: fail("DeepSeek fetch returned empty content")
        return ScrapedResult(listOf(ScrapedResultUrl(url, content,
            ScrapedResultMetadata(title = response.title?.takeIf(String::isNotBlank)))))
    }

    // Only request starts are paced; the mutex is never held during HTTP I/O.
    private suspend fun pace() = pacing.withLock {
        lastStartNanos?.let { last ->
            val waitMs = MIN_INTERVAL_MS - (System.nanoTime() - last) / 1_000_000L
            if (waitMs > 0) delay(waitMs)
        }
        lastStartNanos = System.nanoTime()
    }

    private fun requireKey(key: String): String = key.trim().takeIf(String::isNotBlank)
        ?: fail("DeepSeek API key is required")

    private fun stringParam(params: JsonObject, key: String): String =
        (params[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail("$key is required")

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (_: Exception) {
        // Serializer exceptions may contain the response (and an echoed key).
        fail("DeepSeek returned an invalid response")
    }

    private suspend fun <T> safeResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: DeepSeekFailure) {
        Result.failure(IllegalStateException(error.message))
    } catch (_: Exception) {
        Result.failure(IllegalStateException("DeepSeek request failed. Check the connection and service configuration."))
    }

    private fun failWith(operation: String, code: Int): Nothing {
        val detail = when (code) {
            401 -> "DeepSeek API key is invalid"
            402 -> "DeepSeek account balance is insufficient"
            403 -> "rejected by DeepSeek security policy"
            404 -> "DeepSeek could not retrieve usable page content or the endpoint is unavailable"
            429 -> "DeepSeek rate limit exceeded (5 QPS per account). Retry after a short wait."
            else -> "request failed; check the service or retry later"
        }
        fail("DeepSeek $operation failed (#$code): $detail")
    }

    private fun fail(message: String): Nothing = throw DeepSeekFailure(message)
}

private class DeepSeekFailure(message: String) : IllegalStateException(message)

@Serializable
private data class DeepSeekSearchResponse(val results: List<DeepSeekSearchItem> = emptyList())

@Serializable
private data class DeepSeekSearchItem(
    val title: String? = null, val url: String? = null, val snippet: String? = null,
    val summary: String? = null, val date: String? = null,
)

@Serializable
private data class DeepSeekFetchResponse(val title: String? = null, val content: String? = null)
