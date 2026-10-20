package me.rerere.rikkahub.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSitesTest {
    @Test
    fun normalizesUrlToHostAndMergesWww() {
        assertEquals("github.com", normalizeBrowserVisitedHost("https://WWW.GitHub.com/path?q=secret"))
        assertEquals("gist.github.com", normalizeBrowserVisitedHost("gist.github.com/a"))
    }

    @Test
    fun rejectsLocalAndIpHosts() {
        listOf("localhost", "api.localhost", "127.0.0.1", "192.168.1.20", "https://[::1]/").forEach {
            assertNull(normalizeBrowserVisitedHost(it))
        }
    }

    @Test
    fun parsesOnlyDistinctCookieNames() {
        val names = cookieNamesFromHeader("session=secret-value; theme=dark; session=another-secret")
        assertEquals(listOf("session", "theme"), names)
        assertFalse(names.any { it.contains("secret") })
    }

    @Test
    fun throttlesWritesPerHostWithoutDroppingOtherHosts() {
        val throttle = BrowserVisitThrottle(intervalMs = 5_000)
        assertTrue(throttle.shouldRecord("example.com", 10_000))
        assertFalse(throttle.shouldRecord("example.com", 14_999))
        assertTrue(throttle.shouldRecord("other.example", 14_999))
        assertTrue(throttle.shouldRecord("example.com", 15_000))
    }
}
