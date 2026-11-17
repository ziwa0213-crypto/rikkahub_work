package me.rerere.rikkahub.utils

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import me.rerere.ai.provider.ProviderHttpException
import me.rerere.ai.provider.ProviderResponseException
import me.rerere.ai.provider.VideoInputException
import me.rerere.rikkahub.R
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException
import javax.xml.parsers.DocumentBuilderFactory

class ErrorTextTest {
    private fun resolve(error: Throwable, language: String = "values-zh"): String? {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$language/strings.xml"))
        val elements = document.getElementsByTagName("string")
        val strings = (0 until elements.length).associate { index ->
            val node = elements.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        return ErrorText.resolve(error) { resource, code ->
            val name = R.string::class.java.fields.single { it.getInt(null) == resource }.name
            val template = strings.getValue(name)
            if (code == null) template else String.format(Locale.ROOT, template, code)
        }
    }

    @Test fun knownHttpStatusesHaveLocalizedExplanationsAndOriginalBody() {
        val expected = mapOf(
            401 to "API 密钥无效或已过期，请检查供应商设置",
            403 to "拒绝访问（地区限制 / 余额不足 / 权限不足）",
            404 to "接口或模型不存在，请检查地址与模型 ID",
            429 to "请求过于频繁，请稍后重试",
        )
        expected.forEach { (code, message) ->
            assertEquals("$message\n\n{\"detail\":\"original\"}", resolve(ProviderHttpException(code, " {\"detail\":\"original\"}\n")))
        }
    }

    @Test fun serverErrorsIncludeStatusWithoutBlankBodySuffix() {
        listOf(500, 503, 599).forEach { code ->
            assertEquals("服务端错误（HTTP $code）", resolve(ProviderHttpException(code, " \n ")))
        }
    }

    @Test fun otherHttpStatusTruncatesOnlyDisplayedBody() {
        val error = ProviderHttpException(418, "x".repeat(600))
        assertEquals("请求失败（HTTP 418）\n\n${"x".repeat(400)}", resolve(error))
        assertEquals("Failed to get response: 418 ${"x".repeat(600)}", error.message)
    }

    @Test fun specificNetworkExceptionsTakePrecedenceOverIOException() {
        assertEquals("无法解析服务器地址", resolve(UnknownHostException("raw")))
        assertEquals("连接超时", resolve(SocketTimeoutException("raw")))
        assertEquals("无法连接到服务器", resolve(ConnectException("raw")))
        assertEquals("无法连接到服务器", resolve(NoRouteToHostException("raw")))
        assertEquals("安全连接失败，可能是代理证书问题", resolve(SSLException("raw")))
        assertEquals("文件不存在或已被删除", resolve(FileNotFoundException("raw")))
    }

    @Test fun interruptedConnectionsCoverOkHttpIOExceptionSubclasses() {
        listOf(EOFException(), SocketException(), IOException("stream was reset: CANCEL"),
            object : IOException("connection shutdown") {}).forEach {
            assertEquals("连接被中断（服务器或代理关闭了连接）", resolve(it))
        }
    }

    @Test fun structuredResponseAndSerializationFailuresShareParseExplanation() {
        val expected = "服务器返回的内容无法解析，可能不是兼容接口"
        assertEquals(expected, resolve(SerializationException("Unexpected JSON token")))
        assertEquals(expected, resolve(ProviderResponseException("message is null")))
    }

    @Test fun unknownErrorsAndCancellationRemainUnmapped() {
        assertNull(resolve(IllegalStateException("随便")))
        assertNull(resolve(IllegalArgumentException("Failed to get response: 401")))
        assertNull(resolve(CancellationException("cancelled")))
    }

    @Test fun allVideoReasonsReuseExistingLocalizedMessages() {
        assertTrue(resolve(VideoInputException(VideoInputException.Reason.DISABLED))!!.startsWith("当前模型未开启视频输入"))
        assertTrue(resolve(VideoInputException(VideoInputException.Reason.UNSUPPORTED_PROTOCOL))!!.startsWith("当前供应商协议尚未接入视频输入"))
        assertEquals("无法读取或编码视频，请重新选择视频文件。", resolve(VideoInputException(VideoInputException.Reason.ENCODING_FAILED)))
    }

    @Test fun englishAndTraditionalChineseUseTheirOwnResourceText() {
        val error = ProviderHttpException(401, "")
        assertEquals("API key is invalid or expired. Check the provider settings.", resolve(error, "values"))
        assertEquals("API 金鑰無效或已過期，請檢查供應商設定", resolve(error, "values-zh-rTW"))
    }
}
