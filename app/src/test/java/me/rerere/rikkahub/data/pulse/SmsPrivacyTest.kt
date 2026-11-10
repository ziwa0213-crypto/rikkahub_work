package me.rerere.rikkahub.data.pulse

import org.junit.Assert.*
import org.junit.Test

class SmsPrivacyTest {
    @Test fun discoveryCountsDistinctSendersBeforeMaskingWithoutReturningTheirNumbers() {
        val discovery = smsDiscovery(50, listOf("13812341234", "13856781234", "1381234-1234"), emptyList())
        assertEquals(50L, discovery.maxScannedId)
        assertEquals(3, discovery.senders.size)
        assertEquals(2, discovery.senderCount)
        assertEquals(listOf("138****1234", "138****1234", "138****1234"), discovery.senders)
        val filtered = smsDiscovery(50, listOf("95588", "95588", "10086"), listOf("95588"))
        assertEquals(2, filtered.senders.size)
        assertEquals(1, filtered.senderCount)
        val empty = smsDiscovery(50, listOf("10086"), listOf("95588"))
        assertTrue(empty.senders.isEmpty())
        assertEquals(0, empty.senderCount)
        assertEquals(50L, empty.maxScannedId)
    }
    @Test fun verificationExamples() {
        listOf("验证码 123456", "校验码：4321", "动态码 876543", "动态密码 12345678",
            "登录码 12345", "确认码 654321", "Your verification code is 123456",
            "OTP: 87654321", "G-123456", "【银行】123456", "驗證碼 123456", "認証 123456")
            .forEach { assertTrue(it, isVerificationSms(it)) }
    }
    @Test fun ordinaryExamples() {
        listOf("快递单号 123456", "订单 12345678 已发货", "明天三点开会", "余额 123.45 元",
            "验证码将在稍后发送", "code ABC", "code 123", "code 123456789",
            "运单 123456789012", "Package 654321 delivered", "会议号 123456", "G-123")
            .forEach { assertFalse(it, isVerificationSms(it)) }
    }
    @Test fun redactsWholeBodyBeforeTruncatingAndNeverExposesCode() {
        val body = "a".repeat(2200) + "验证码 123456"
        val entry = redactedSmsEntry(1, "13812341234", body, 0, emptyList())!!
        assertNull(entry.body)
        assertEquals("verification_code", entry.redactedKind)
        assertFalse(entry.truncated)
        assertEquals("138****1234", entry.address)
    }
    @Test fun normalizesWhitelistAndKeepsServiceCodesAndTruncatesOrdinaryBody() {
        assertEquals("95588", maskSmsAddress("95588", emptyList()))
        assertEquals("+8613812341234", maskSmsAddress("+86 (138) 1234-1234", listOf("+86 13812341234")))
        assertTrue(smsSenderAllowed("(138)1234-1234", listOf("13812341234")))
        assertNull(redactedSmsEntry(1, "other", "hello", 0, listOf("95588")))
        assertNull(redactedSmsEntry(1, "95588", "  ", 0, emptyList()))
        val long = redactedSmsEntry(1, "95588", "x".repeat(2500), 0, emptyList())!!
        assertEquals(2000, long.body!!.length)
        assertTrue(long.truncated)
    }
    @Test fun validatesDestinationAndFullMessageWithoutEchoingSensitiveInput() {
        assertEquals("+8613812341234", validateSms("+86 (138) 1234-1234", "x".repeat(1000)))
        listOf("", "abc", "1234", "1".repeat(21), "+12*34").forEach {
            assertTrue(runCatching { validateSms(it, "hello") }.isFailure)
        }
        listOf("", " ", "x".repeat(1001)).forEach {
            assertTrue(runCatching { validateSms("95588", it) }.isFailure)
        }
    }
}
