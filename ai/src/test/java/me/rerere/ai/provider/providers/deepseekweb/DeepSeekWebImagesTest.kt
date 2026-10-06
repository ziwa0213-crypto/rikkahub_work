package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebImagesTest {
    private fun client(responder: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (status, body) = responder(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(status).message("test").body(body.toResponseBody()).build()
    }.build()

    private fun success(id: String = "file-1") = """{"code":0,"data":{"biz_code":0,"biz_data":{"id":"$id"}}}"""
    private fun message(vararg urls: String) = UIMessage.user("").copy(parts = urls.map { UIMessagePart.Image(it) })

    @Test
    fun uploadsMultipartWithSupportedSuffixAndTargetSpecificProof() = runBlocking {
        var calls = 0
        val client = client { request ->
            calls++
            assertEquals(DeepSeekWebImages.UPLOAD_PATH, request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("Bearer test-token", request.header("Authorization"))
            assertEquals("upload-proof", request.header("x-ds-pow-response"))
            assertNull(request.header("Content-Type"))
            assertTrue(request.body!!.contentType().toString().startsWith("multipart/form-data; boundary="))
            val multipart = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            assertTrue(multipart.contains("name=\"file\"; filename=\"image.png\""))
            assertTrue(multipart.contains("Content-Type: image/png"))
            assertTrue(multipart.contains("encoded-png"))
            200 to success()
        }
        val images = DeepSeekWebImages(client, powHeader = { headers, target ->
            assertEquals(DeepSeekWebImages.UPLOAD_PATH, target)
            assertEquals("Bearer test-token", headers["Authorization"])
            "upload-proof"
        }, readImage = { DeepSeekWebImages.ImageData("encoded-png".toByteArray(), "image/png") })
        val result = images.prepare(listOf(message("file:///sensitive-name-without-extension")), mapOf(
            "Authorization" to "Bearer test-token", "cOnTeNt-TyPe" to "application/json",
        ))
        assertEquals(1, calls)
        assertEquals(listOf("file-1"), result.fileIds)
        assertEquals(1, result.references["file:///sensitive-name-without-extension"])
    }

    @Test
    fun deduplicatesRepeatedUrlsAndIdenticalBytesAcrossMessages() = runBlocking {
        var uploads = 0
        var reads = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success("file-$uploads") }, { _, _ -> "proof" }) {
            reads++
            DeepSeekWebImages.ImageData(byteArrayOf(1, 2, 3), "image/jpeg")
        }
        val result = images.prepare(listOf(message("file:///a", "file:///a"), message("file:///b")), emptyMap())
        assertEquals(2, reads)
        assertEquals(1, uploads)
        assertEquals(listOf("file-1"), result.fileIds)
        assertEquals(mapOf("file:///a" to 1, "file:///b" to 1), result.references)
    }

    @Test
    fun keepsDifferentImagesInPromptOrder() = runBlocking {
        var uploads = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success("file-$uploads") }, { _, _ -> "proof" }) {
            DeepSeekWebImages.ImageData(it.url.toByteArray(), "image/png")
        }
        val result = images.prepare(listOf(message("file:///b", "file:///a")), emptyMap())
        assertEquals(listOf("file-1", "file-2"), result.fileIds)
        assertEquals(mapOf("file:///b" to 1, "file:///a" to 2), result.references)
    }

    @Test
    fun textOnlyRequestsDoNotReadUploadOrSolveProof() = runBlocking {
        val images = DeepSeekWebImages(client { error("Must not upload") }, { _, _ -> error("Must not solve PoW") }) {
            error("Must not read an image")
        }
        val result = images.prepare(listOf(UIMessage.user("hello")), emptyMap())
        assertTrue(result.fileIds.isEmpty())
        assertTrue(result.references.isEmpty())
    }

    @Test
    fun refusesTooManyAttachmentsBeforeAnyUpload() {
        var uploads = 0
        var proofs = 0
        var reads = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success("file-$uploads") }, { _, _ -> proofs++; "proof" }) {
            reads++
            DeepSeekWebImages.ImageData(it.url.toByteArray(), "image/png")
        }
        assertThrows(DeepSeekWebImageException::class.java) {
            runBlocking { images.prepare(listOf(message(*(1..25).map { "file:///$it" }.toTypedArray())), emptyMap()) }
        }
        assertEquals(0, reads)
        assertEquals(0, proofs)
        assertEquals(0, uploads)
    }

    @Test
    fun acceptsExactly24Attachments() = runBlocking {
        var uploads = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success("file-$uploads") }, { _, _ -> "proof" }) {
            DeepSeekWebImages.ImageData(it.url.toByteArray(), "image/png")
        }
        val result = images.prepare(listOf(message(*(1..24).map { "file:///$it" }.toTypedArray())), emptyMap())
        assertEquals(24, result.fileIds.size)
        assertEquals(24, uploads)
    }

    @Test
    fun uploadFailureStopsRemainingAttachmentsAndDoesNotExposeResponseBody() {
        var uploads = 0
        val images = DeepSeekWebImages(client { uploads++; 401 to "Bearer private-token image-data" }, { _, _ -> "proof" }) {
            DeepSeekWebImages.ImageData(it.url.toByteArray(), "image/png")
        }
        val failure = assertThrows(DeepSeekWebImageException::class.java) {
            runBlocking { images.prepare(listOf(message("file:///a", "file:///b")), emptyMap()) }
        }
        assertEquals("Upload HTTP 401", failure.message)
        assertEquals(1, uploads)
        assertNull(failure.cause)
    }

    @Test
    fun rejectsBusinessErrorsMissingIdsAndWrongJsonTypes() {
        listOf(
            "[]", "not JSON", "{}",
            """{"code":0,"data":{"biz_code":9,"biz_data":{"id":"file-1"}}}""",
            """{"code":1,"data":{"biz_code":0,"biz_data":{"id":"file-1"}}}""",
            """{"data":{"id":[]}}""", """{"data":{"id":123}}""", """{"data":{"id":""}}""",
        ).forEach { response ->
            assertThrows(DeepSeekWebImageException::class.java) { DeepSeekWebImages.parseUploadResponse(response) }
        }
        assertEquals("fallback", DeepSeekWebImages.parseUploadResponse("""{"code":0,"data":{"biz_code":0,"id":"fallback"}}"""))
    }

    @Test
    fun rejectsUnsupportedOrOversizedInlineImagesWithoutNetwork() {
        var uploads = 0
        var proofs = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success() }, { _, _ -> proofs++; "proof" })
        listOf(
            "https://example.invalid/image.png", "data:image/bmp;base64,AQID", "data:image/png;base64,!invalid!",
            "data:image/png;base64," + "A".repeat(14 * 1024 * 1024),
        ).forEach { url ->
            assertThrows(DeepSeekWebImageException::class.java) { runBlocking { images.prepare(listOf(message(url)), emptyMap()) } }
        }
        assertEquals(0, uploads)
        assertEquals(0, proofs)
    }

    @Test
    fun cancellationIsNotConvertedToAnUploadError() {
        val images = DeepSeekWebImages(client { error("Must not upload") }, { _, _ -> throw CancellationException("cancelled") }) {
            DeepSeekWebImages.ImageData(byteArrayOf(1), "image/png")
        }
        assertThrows(CancellationException::class.java) { runBlocking { images.prepare(listOf(message("file:///a")), emptyMap()) } }
    }

    @Test
    fun completionBodyReferencesImagesInBothModes() {
        for (thinking in listOf(false, true)) {
            val body = deepSeekWebCompletionBody("session", "prompt", thinking, listOf("file-1", "file-2"))
            assertEquals(listOf("file-1", "file-2"), body["ref_file_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(thinking, body["thinking_enabled"]!!.jsonPrimitive.boolean)
            assertEquals("default", body["model_type"]!!.jsonPrimitive.content)
            assertFalse(body.containsKey("image_url"))
        }
    }

    @Test
    fun uploadsReadOnlyToolImageOutputAndReturnsItsReferenceInPrompt() = runBlocking {
        var uploads = 0
        val images = DeepSeekWebImages(client { uploads++; 200 to success() }, { _, _ -> "proof" })
        val messages = listOf(UIMessage.assistant("").copy(parts = listOf(
            UIMessagePart.Tool("read-image", "workspace_read_file", """{"path":"/workspace/a.png"}""",
                listOf(UIMessagePart.Image("data:image/png;base64,AQID"))),
            UIMessagePart.Tool("blocked", "workspace_write_file", "{}",
                listOf(UIMessagePart.Image("data:image/png;base64,BAUG"))),
        )))
        val uploaded = images.prepare(messages, emptyMap())
        val prompt = DeepSeekWebTools.prompt(messages, emptyList(), DeepSeekWebModels.defaults().first(), uploaded.references)
        assertEquals(1, uploads)
        assertTrue(prompt.contains("工具返回结果：\n[Image attachment 1]"))
        assertFalse(prompt.contains("[Image not uploaded]"))
    }

    @Test
    fun promptMarkersCorrespondToUploadedReferences() = runBlocking {
        val images = DeepSeekWebImages(client { 200 to success() }, { _, _ -> "proof" })
        val messages = listOf(message("data:image/png;base64,AQID"))
        val uploaded = images.prepare(messages, emptyMap())
        val prompt = DeepSeekWebTools.prompt(messages, emptyList(), DeepSeekWebModels.defaults().first(), uploaded.references)
        assertTrue(prompt.contains("[Image attachment 1]"))
        assertFalse(prompt.contains("AQID"))
        assertFalse(prompt.contains("[Image not uploaded]"))
    }
}
