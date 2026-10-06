package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.encodeBase64
import me.rerere.ai.util.json
import me.rerere.common.http.await
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64

class DeepSeekWebImageException(reason: String) : IllegalStateException(reason)

internal class DeepSeekWebImages(
    private val client: OkHttpClient,
    private val powHeader: suspend (Map<String, String>, String) -> String,
    private val readImage: (UIMessagePart.Image) -> ImageData = ::readImageData,
) {
    data class ImageData(val bytes: ByteArray, val mimeType: String)
    data class UploadedImages(val fileIds: List<String>, val references: Map<String, Int>)

    suspend fun prepare(messages: List<UIMessage>, headers: Map<String, String>): UploadedImages {
        val images = messages.flatMap { message -> message.parts.flatMap { part ->
            if (part is UIMessagePart.Tool && DeepSeekWebToolPolicy.allows(part.toolName,
                    part.inputAsJson() as? JsonObject ?: JsonObject(emptyMap()))) part.output
            else listOf(part)
        } }.filterIsInstance<UIMessagePart.Image>().distinctBy { it.url }
        if (images.size > MAX_IMAGES) throw DeepSeekWebImageException("At most $MAX_IMAGES image attachments per request")
        val idsByHash = linkedMapOf<String, String>()
        val references = linkedMapOf<String, Int>()
        for (image in images) {
            try {
                val data = readImage(image)
                if (data.bytes.isEmpty() || data.bytes.size > MAX_IMAGE_BYTES) {
                    throw DeepSeekWebImageException("Encoded image must be between 1 byte and 10 MB")
                }
                val extension = extensions[data.mimeType]
                    ?: throw DeepSeekWebImageException("Unsupported image type")
                val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data.bytes))
                val id = idsByHash[hash] ?: upload(data, extension, headers).also { idsByHash[hash] = it }
                val uniqueIds = idsByHash.values.distinct()
                references[image.url] = uniqueIds.indexOf(id) + 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: DeepSeekWebImageException) {
                throw error
            } catch (_: Exception) {
                // Do not expose file paths, data URLs or authentication headers in error logs.
                throw DeepSeekWebImageException("Image read, encoding or upload failed")
            }
        }
        return UploadedImages(idsByHash.values.distinct(), references)
    }

    private suspend fun upload(data: ImageData, extension: String, headers: Map<String, String>): String {
        val proof = powHeader(headers, UPLOAD_PATH)
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "image.$extension", data.bytes.toRequestBody(data.mimeType.toMediaType()))
            .build()
        val uploadHeaders = Headers.Builder().apply {
            headers.forEach { (name, value) ->
                if (!name.equals("content-type", ignoreCase = true)) add(name, value)
            }
        }.build()
        val request = Request.Builder().url("https://chat.deepseek.com$UPLOAD_PATH")
            .headers(uploadHeaders).header("x-ds-pow-response", proof).post(multipart).build()
        return client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw DeepSeekWebImageException("Upload HTTP ${response.code}")
            parseUploadResponse(response.body.string())
        }
    }

    companion object {
        const val UPLOAD_PATH = "/api/v0/file/upload_file"
        const val MAX_IMAGES = 24
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private val extensions = mapOf("image/png" to "png", "image/jpeg" to "jpg", "image/webp" to "webp", "image/gif" to "gif")

        internal fun parseUploadResponse(body: String): String {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: throw DeepSeekWebImageException("Invalid upload response")
            val data = root["data"] as? JsonObject
            for (code in listOf(root["code"], data?.get("biz_code"))) {
                if (code != null && (code as? JsonPrimitive)?.intOrNull != 0) {
                    throw DeepSeekWebImageException("Upload rejected by service")
                }
            }
            val id = ((data?.get("biz_data") as? JsonObject)?.get("id") ?: data?.get("id")) as? JsonPrimitive
            return id?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw DeepSeekWebImageException("Upload response has no file ID")
        }

        private fun readImageData(image: UIMessagePart.Image): ImageData {
            val encoded = image.encodeBase64().getOrThrow()
            val prefix = "data:${encoded.mimeType};base64,"
            if (!encoded.base64.startsWith(prefix)) throw DeepSeekWebImageException("Only local or inline images can be uploaded")
            val content = encoded.base64.removePrefix(prefix)
            if (content.length > ((MAX_IMAGE_BYTES + 2L) / 3 * 4)) {
                throw DeepSeekWebImageException("Encoded image exceeds 10 MB")
            }
            return ImageData(Base64.getDecoder().decode(content), encoded.mimeType)
        }
    }
}
