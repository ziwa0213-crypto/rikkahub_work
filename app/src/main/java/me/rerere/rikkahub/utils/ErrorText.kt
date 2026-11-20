package me.rerere.rikkahub.utils

import android.content.Context
import kotlinx.serialization.SerializationException
import me.rerere.ai.provider.ProviderHttpException
import me.rerere.ai.provider.ProviderResponseException
import me.rerere.ai.provider.VideoInputException
import me.rerere.rikkahub.R
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Localizes only user-facing chat errors, without changing provider diagnostics or tool results. */
object ErrorText {
    fun resolve(context: Context, error: Throwable): String? = resolve(error) { resource, code ->
        if (code == null) context.getString(resource) else context.getString(resource, code)
    }

    internal fun resolve(error: Throwable, getString: (Int, Int?) -> String): String? {
        val resource = when (error) {
            is ProviderHttpException -> return httpMessage(error, getString)
            is ProviderResponseException -> R.string.error_resp_invalid_json
            is VideoInputException -> when (error.reason) {
                VideoInputException.Reason.DISABLED -> R.string.chat_error_video_disabled
                VideoInputException.Reason.UNSUPPORTED_PROTOCOL -> R.string.chat_error_video_protocol
                VideoInputException.Reason.ENCODING_FAILED -> R.string.chat_error_video_encoding
            }
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            is SSLException -> R.string.error_network_ssl
            is FileNotFoundException -> R.string.error_file_not_found
            is EOFException, is SocketException -> R.string.error_network_interrupted
            is IOException -> R.string.error_network_interrupted
            is SerializationException -> R.string.error_resp_invalid_json
            else -> return null
        }
        return getString(resource, null)
    }

    private fun httpMessage(error: ProviderHttpException, getString: (Int, Int?) -> String): String {
        if (error.body.contains("unknown variant", ignoreCase = true) && error.body.contains("video_url", ignoreCase = true))
            return getString(R.string.video_not_supported_by_provider, null)
        val resource = when (error.code) {
            401 -> R.string.error_http_401
            403 -> R.string.error_http_403
            404 -> R.string.error_http_404
            429 -> R.string.error_http_429
            in 500..599 -> R.string.error_http_5xx
            else -> R.string.error_http_other
        }
        val base = getString(resource, error.code.takeUnless { it in setOf(401, 403, 404, 429) })
        val body = error.body.trim().take(400)
        return if (body.isEmpty()) base else "$base\n\n$body"
    }
}
