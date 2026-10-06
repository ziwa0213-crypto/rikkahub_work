package me.rerere.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_RESULTS = 20

object SuiXiangSearchService : SearchService<SearchServiceOptions.SuiXiangOptions> {
    private const val PATH = "/v1/web_search"

    override val name: String = "随想搜索"

    @Composable
    override fun Description() {
        Text(stringResource(R.string.search_suixiang_web_description))
    }

    override fun parameters(options: SearchServiceOptions.SuiXiangOptions): InputSchema =
        suixiangParameters()

    override fun scrapingParameters(options: SearchServiceOptions.SuiXiangOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SuiXiangOptions
    ): Result<SearchResult> = searchSuiXiang(
        path = PATH,
        params = params,
        commonOptions = commonOptions,
        apiKey = serviceOptions.apiKey,
        baseUrl = serviceOptions.baseUrl,
        maxResults = serviceOptions.maxResults,
    )

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SuiXiangOptions
    ): Result<ScrapedResult> = unsupportedScrape()

    internal fun parseResponse(responseBody: String, maxResults: Int): SearchResult =
        parseSuiXiangResponse(responseBody, maxResults)

    internal fun buildRequest(
        query: String,
        options: SearchServiceOptions.SuiXiangOptions,
        resultSize: Int,
        apiKey: String = options.apiKey.trim(),
    ): Request = buildSuiXiangRequest(PATH, query, options.baseUrl, resultSize, apiKey)
}

private fun suixiangParameters(): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("query", buildJsonObject {
            put("type", "string")
            put("description", "search keyword")
        })
    },
    required = listOf("query")
)

private suspend fun searchSuiXiang(
    path: String,
    params: JsonObject,
    commonOptions: SearchCommonOptions,
    apiKey: String,
    baseUrl: String,
    maxResults: Int,
): Result<SearchResult> = withContext(Dispatchers.IO) {
    runCatching {
        val requestApiKey = apiKey.trim()
        if (requestApiKey.isBlank()) error("API key is required")

        val query = params["query"]?.jsonPrimitive?.content
            ?: error("query is required")
        val resultSize = minOf(maxResults.coerceIn(1, MAX_RESULTS), commonOptions.resultSize.coerceIn(1, MAX_RESULTS))
        val request = buildSuiXiangRequest(path, query, baseUrl, resultSize, requestApiKey)

        httpClient.newCall(request).await().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                error("response failed #${response.code}: ${responseBody.replace(requestApiKey, "[redacted]").take(300)}")
            }
            parseSuiXiangResponse(responseBody, resultSize)
        }
    }
}

private fun buildSuiXiangRequest(
    path: String,
    query: String,
    baseUrl: String,
    resultSize: Int,
    apiKey: String,
): Request {
    val body = buildJsonObject {
        put("query", JsonPrimitive(query))
        put("max_results", JsonPrimitive(resultSize.coerceIn(1, MAX_RESULTS)))
    }
    return Request.Builder()
        .url(baseUrl.trim().trimEnd('/') + path)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .build()
}

private fun parseSuiXiangResponse(responseBody: String, maxResults: Int): SearchResult {
    val response = json.decodeFromString<SuiXiangResponse>(responseBody)
    return SearchResult(
        items = response.results.mapNotNull { item ->
            val url = item.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            SearchResultItem(
                title = item.title.orEmpty(),
                url = url,
                text = item.snippet.orEmpty(),
            )
        }.take(maxResults),
    )
}

private fun unsupportedScrape(): Result<ScrapedResult> =
    Result.failure(Exception("Scraping is not supported for SuiXiang"))

@Serializable
private data class SuiXiangResponse(
    @SerialName("max_results") val maxResults: Int? = null,
    val provider: String? = null,
    val query: String? = null,
    val results: List<SuiXiangItem> = emptyList(),
)

@Serializable
private data class SuiXiangItem(
    val url: String? = null,
    val title: String? = null,
    val snippet: String? = null,
)
