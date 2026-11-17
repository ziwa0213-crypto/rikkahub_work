package me.rerere.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ProviderHttpExceptionTest {
    @Test fun httpDiagnosticsRetainExactOriginalText() {
        listOf("", "null", " {\"error\":\"invalid key\"}\n", "x".repeat(600)).forEach { body ->
            val error = ProviderHttpException(401, body)
            assertEquals("Failed to get response: 401 $body", error.message)
            assertEquals(body, error.body)
            assertEquals(401, error.code)
        }
    }

    @Test fun invalidResponseDiagnosticsRetainExactOriginalText() {
        assertEquals("message is null", ProviderResponseException("message is null").message)
    }

    @Test fun streamingHttpErrorsKeepCodeBodyAndOriginalFailureCause() {
        for (body in listOf("", "{\"error\":\"bad key\"}", "<html>upstream unavailable</html>")) {
            val original = IllegalStateException("original diagnostic")
            val error = providerStreamFailure(401, body, original) as ProviderHttpException
            assertEquals(401, error.code)
            assertEquals(body, error.body)
            assertSame(original, error.cause)
        }
        assertEquals(503, (providerStreamFailure(503, null, null) as ProviderHttpException).code)
    }

    @Test fun streamingNetworkFailureAndCancellationKeepTheirTypeAndIdentity() {
        val original = IOException("interrupted")
        assertSame(original, providerStreamFailure(null, null, original))
        assertSame(original, providerStreamFailure(200, "", original))
        assertNull(providerStreamFailure(200, "", null))
    }

    @Test fun declaredIncompatibleSuccessfulResponseIsAParseFailure() {
        val original = IllegalStateException("Invalid content-type: text/html")
        val error = providerStreamFailure(200, "<html>not an API</html>", original, "text/html; charset=utf-8") as ProviderResponseException
        assertEquals(original.message, error.message)
        assertSame(original, error.cause)
        assertSame(original, providerStreamFailure(200, null, original, "text/event-stream"))
        assertSame(original, providerStreamFailure(200, null, original))
    }
}
