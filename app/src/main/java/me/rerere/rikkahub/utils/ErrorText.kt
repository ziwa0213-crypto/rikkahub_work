package me.rerere.rikkahub.utils

import android.content.Context
import kotlinx.serialization.SerializationException
import me.rerere.ai.provider.ProviderHttpException
import me.rerere.ai.provider.ProviderResponseException
import me.rerere.ai.provider.VideoInputException
import me.rerere.rikkahub.R
import me.rerere.llamacpp.LocalModelException
import me.rerere.llamacpp.LocalModelError
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
        if (resource == R.string.local_llm_error_no_space && error is LocalModelException) {
            val sizes = Regex("need (\\d+) MB, free (\\d+) MB").find(error.detail.orEmpty())
            if (sizes != null) context.getString(resource, sizes.groupValues[1], sizes.groupValues[2])
            else context.getString(R.string.local_llm_error_no_space_generic)
        } else if (code == null) context.getString(resource) else context.getString(resource, code)
    }

    internal fun resolve(error: Throwable, getString: (Int, Int?) -> String): String? {
        val resource = when (error) {
            is LocalModelException -> when (error.reason) {
                LocalModelError.MEMORY -> R.string.local_llm_error_not_enough_memory
                LocalModelError.TOO_LONG -> {
                    fun value(key: String) = Regex("(?:^|\\s)${Regex.escape(key)}${if (key.endsWith("≈")) "" else "="}(-?\\d+)")
                        .find(error.detail.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 0 }
                    val nCtx = value("nCtx")
                    val input = value("input")
                    return when {
                        nCtx != null && input != null -> String.format(
                            java.util.Locale.getDefault(), getString(R.string.local_llm_error_too_long_detail, null),
                            nCtx.toString(), input.toString(), value("system≈")?.toString() ?: "?",
                            value("tools≈")?.toString() ?: "?", value("hist")?.toString() ?: "?",
                        )
                        nCtx != null -> getString(R.string.local_llm_error_too_long_ctx, nCtx)
                        else -> getString(R.string.local_llm_error_prompt_too_long, null)
                    }
                }
                LocalModelError.BAD_FILE -> R.string.local_llm_error_bad_file
                LocalModelError.DOWNLOAD -> R.string.local_llm_error_download
                LocalModelError.NOT_INSTALLED -> R.string.local_llm_not_installed
                LocalModelError.NO_SPACE -> R.string.local_llm_error_no_space
                LocalModelError.EMPTY_OUTPUT -> R.string.local_llm_error_empty_output
                LocalModelError.GPU_LOST -> R.string.local_llm_error_gpu_lost
            }
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
