package me.rerere.ai.provider

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Real provider entry points with an in-process interceptor; no socket, account or server is used. */
class ProviderHttpFailuresTest {
    private suspend fun request(protocol: Int, client: OkHttpClient, stream: Boolean) {
        val messages = listOf(UIMessage.user("offline fixture"))
        val params = TextGenerationParams(model = Model(modelId = "fixture-model"))
        when (protocol) {
            0, 1 -> {
                val provider = OpenAIProvider(client)
                val setting = ProviderSetting.OpenAI(apiKey = "fixture-key", baseUrl = "https://offline.invalid/v1", useResponseApi = protocol == 1)
                if (stream) provider.streamText(setting, messages, params).toList()
                else provider.generateText(setting, messages, params)
            }
            2 -> {
                val provider = ClaudeProvider(client)
                val setting = ProviderSetting.Claude(apiKey = "fixture-key", baseUrl = "https://offline.invalid/v1")
                if (stream) provider.streamText(setting, messages, params).toList()
                else provider.generateText(setting, messages, params)
            }
            else -> {
                val provider = GoogleProvider(client)
                val setting = ProviderSetting.Google(apiKey = "fixture-key", baseUrl = "https://offline.invalid", useInteractionsApi = protocol == 4)
                if (stream) provider.streamText(setting, messages, params).toList()
                else provider.generateText(setting, messages, params)
            }
        }
    }

    private fun verify(stream: Boolean) = runBlocking {
        withTimeout(15_000) {
            for (protocol in 0..4) {
                for ((code, body) in listOf(401 to "{\"error\":{\"message\":\"invalid key\"}}", 503 to "<html>unavailable</html>")) {
                    var requests = 0
                    val client = OkHttpClient.Builder().addInterceptor { chain ->
                        requests++
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(code).message("fixture")
                            .body(RealResponseBody("application/json", body.toByteArray().size.toLong(), Buffer().writeUtf8(body))).build()
                    }.build()
                    try {
                        val failure = try {
                            request(protocol, client, stream)
                            null
                        } catch (error: ProviderHttpException) {
                            error
                        }
                        assertNotNull("protocol=$protocol stream=$stream code=$code", failure)
                        assertEquals(code, failure!!.code)
                        assertEquals(body, failure.body)
                        assertEquals("Failed to get response: $code $body", failure.message)
                        assertEquals(1, requests)
                    } finally {
                        client.dispatcher.executorService.shutdown()
                        client.connectionPool.evictAll()
                    }
                }
                if (stream) {
                    val client = OkHttpClient.Builder().addInterceptor { chain ->
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                            .header("Content-Type", "text/html")
                            .body(RealResponseBody("text/html", 23, Buffer().writeUtf8("<html>not an API</html>"))).build()
                    }.build()
                    try {
                        val failure = try {
                            request(protocol, client, stream = true)
                            null
                        } catch (error: ProviderResponseException) {
                            error
                        }
                        assertNotNull("incompatible successful response protocol=$protocol", failure)
                        assertNotNull(failure!!.cause)
                    } finally {
                        client.dispatcher.executorService.shutdown()
                        client.connectionPool.evictAll()
                    }
                }
            }
        }
    }

    @Test fun nonStreamingHttpFailuresKeepStructuredStatusAndOriginalDiagnostic() = verify(stream = false)
    @Test fun streamingHttpFailuresKeepStructuredStatusEvenWithNonJsonBodies() = verify(stream = true)
}
