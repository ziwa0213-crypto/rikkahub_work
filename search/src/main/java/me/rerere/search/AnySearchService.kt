package me.rerere.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

private const val BASE_URL = "https://api.anysearch.com"
private const val MAX_RESULTS = 10

object AnySearchService : SearchService<SearchServiceOptions.AnySearchOptions> {
    override val name = "AnySearch"

    @Composable
    override fun Description() {
        val uriHandler = LocalUriHandler.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.search_anysearch_description))
            TextButton(onClick = { uriHandler.openUri("https://www.anysearch.com/") }) {
                Text(stringResource(R.string.search_anysearch_website))
            }
            TextButton(onClick = { uriHandler.openUri("https://anysearch.com/console/api-keys") }) {
                Text(stringResource(R.string.click_to_get_api_key))
            }
        }
    }

    override fun parameters(options: SearchServiceOptions.AnySearchOptions) = InputSchema.Obj(
        properties = buildJsonObject {
            put("query", buildJsonObject { put("type", "string"); put("description", "Search keyword") })
        },
        required = listOf("query"),
    )

    override fun scrapingParameters(options: SearchServiceOptions.AnySearchOptions) = InputSchema.Obj(
        properties = buildJsonObject {
            put("url", buildJsonObject { put("type", "string"); put("description", "HTTP or HTTPS page URL to extract") })
        },
        required = listOf("url"),
    )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.AnySearchOptions,
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        val key = serviceOptions.apiKey.trim()
        safeResult {
            val query = stringParam(params, "query")
            val size = minOf(serviceOptions.maxResults.coerceIn(1, MAX_RESULTS),
                commonOptions.resultSize.coerceIn(1, MAX_RESULTS))
            httpClient.newCall(buildRequest(query, size, key)).await().use { response ->
                if (!response.isSuccessful) failWith("search", response.code)
                parseResponse(response.body.string(), size, key)
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.AnySearchOptions,
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        val key = serviceOptions.apiKey.trim()
        safeResult {
            val url = stringParam(params, "url")
            httpClient.newCall(buildExtract(url, key)).await().use { response ->
                if (!response.isSuccessful) failWith("extract", response.code)
                parseExtract(response.body.string(), url, key)
            }
        }
    }

    internal fun buildRequest(query: String, resultSize: Int, apiKey: String = ""): Request {
        if (query.isBlank()) fail("query is required")
        return request("/v1/search", apiKey, buildJsonObject {
            put("query", query)
            put("max_results", resultSize.coerceIn(1, MAX_RESULTS))
        })
    }

    internal fun buildExtract(url: String, apiKey: String = ""): Request {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            fail("AnySearch extract requires an HTTP(S) URL without credentials")
        }
        return request("/v1/extract", apiKey, buildJsonObject { put("url", url) })
    }

    private fun request(path: String, apiKey: String, body: JsonObject): Request {
        val builder = Request.Builder().url(BASE_URL + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        // No empty Bearer header, and never retry an invalid key as anonymous.
        apiKey.trim().takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    internal fun parseResponse(responseBody: String, maxResults: Int, apiKey: String = ""): SearchResult {
        val response = decode<AnySearchResponse>(responseBody)
        checkCode(response.code, response.message, apiKey)
        return SearchResult(items = response.data?.results.orEmpty().mapNotNull { item ->
            val url = item.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val snippet = item.snippet.orEmpty()
            val content = item.content.orEmpty()
            SearchResult.SearchResultItem(title = item.title.orEmpty(), url = url,
                text = if (content.length > snippet.length) content else snippet)
        }.take(maxResults.coerceIn(1, MAX_RESULTS)))
    }

    internal fun parseExtract(responseBody: String, url: String, apiKey: String = ""): ScrapedResult {
        val response = decode<AnySearchExtractResponse>(responseBody)
        checkCode(response.code, response.message, apiKey)
        val content = response.data?.content?.takeIf(String::isNotBlank)
            ?: fail("AnySearch extract returned empty content")
        return ScrapedResult(listOf(ScrapedResultUrl(url, content,
            ScrapedResultMetadata(title = response.data.title?.takeIf(String::isNotBlank)))))
    }

    private fun checkCode(code: Int, message: String?, apiKey: String) {
        if (code == 0) return
        val safe = message.orEmpty().let { if (apiKey.isBlank()) it else it.replace(apiKey, "[redacted]") }
        fail("AnySearch error (#$code): ${safe.take(300).ifBlank { "unknown" }}")
    }

    private fun stringParam(params: JsonObject, key: String): String =
        (params[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail("$key is required")

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (_: Exception) {
        fail("AnySearch returned an invalid response")
    }

    private suspend fun <T> safeResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: AnySearchFailure) {
        Result.failure(IllegalStateException(error.message))
    } catch (_: Exception) {
        Result.failure(IllegalStateException("AnySearch request failed. Check the connection and service configuration."))
    }

    private fun failWith(operation: String, code: Int): Nothing {
        val detail = when (code) {
            401 -> "AnySearch API key is invalid or expired. Remove the key to use anonymous access."
            403 -> "AnySearch rejected the request. The key may be disabled or out of quota."
            429 -> "AnySearch rate limit exceeded. Retry later."
            else -> "request failed; check the service or retry later"
        }
        fail("AnySearch $operation failed (#$code): $detail")
    }

    private fun fail(message: String): Nothing = throw AnySearchFailure(message)
}

private class AnySearchFailure(message: String) : IllegalStateException(message)

@Serializable
private data class AnySearchResponse(val code: Int, val message: String? = null, val data: AnySearchData? = null)
@Serializable
private data class AnySearchData(val results: List<AnySearchItem> = emptyList())
@Serializable
private data class AnySearchItem(val title: String? = null, val url: String? = null, val snippet: String? = null, val content: String? = null)
@Serializable
private data class AnySearchExtractResponse(val code: Int, val message: String? = null, val data: AnySearchExtractData? = null)
@Serializable
private data class AnySearchExtractData(val title: String? = null, val content: String? = null)
