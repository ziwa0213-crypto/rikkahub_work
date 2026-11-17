package me.rerere.ai.provider

/** Structured metadata for presentation; preserve the original diagnostic message. */
class ProviderHttpException(
    val code: Int,
    val body: String,
    val url: String? = null,
) : Exception("Failed to get response: $code $body")

class ProviderResponseException(val detail: String) : Exception(detail)

/** An SSE failure with a non-2xx response is an HTTP error, even if its body is not JSON. */
internal fun providerStreamFailure(
    code: Int?,
    body: String?,
    failure: Throwable?,
    contentType: String? = null,
): Throwable? {
    if (code == null) return failure
    if (code in 200..299) {
        // The shared SSE transport explicitly rejects a declared non-event-stream response.
        if (contentType != null && !contentType.substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)) {
            return ProviderResponseException(failure?.message ?: body.orEmpty()).apply {
                if (failure != null) initCause(failure)
            }
        }
        return failure
    }
    return ProviderHttpException(code, body.orEmpty()).apply {
        if (failure != null) initCause(failure)
    }
}
