package me.rerere.rikkahub.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserHighRiskTest {
    @Test
    fun highRiskLabelsAreRejected() {
        assertEquals(BrowserHighRisk.Kind.PAYMENT, BrowserHighRisk.forClick("button", "", "Pay now")?.kind)
        assertEquals(BrowserHighRisk.Kind.DELETE, BrowserHighRisk.forClick("button", "", "删除账户")?.kind)
        assertEquals(BrowserHighRisk.Kind.PUBLISH, BrowserHighRisk.forClick("button", "", "Submit")?.kind)
        assertEquals(BrowserHighRisk.Kind.TRANSFER, BrowserHighRisk.forClick("button", "", "Transfer")?.kind)
    }

    @Test
    fun passwordAndFileInputsAreRejected() {
        assertNotNull(BrowserHighRisk.forType("input", "password"))
        assertEquals(BrowserHighRisk.Kind.FILE, BrowserHighRisk.forType("input", "file")?.kind)
    }

    @Test
    fun ordinaryReadOnlyActionIsAllowed() {
        assertNull(BrowserHighRisk.forClick("a", "", "Read article"))
        assertNull(BrowserHighRisk.forType("input", "text", "Search"))
    }

    @Test fun authorizationFileAndFormSubmissionCannotBeApprovedAway() {
        assertEquals(BrowserHighRisk.Kind.AUTHORIZATION, BrowserHighRisk.forClick("button", "", "Allow access")?.kind)
        assertEquals(BrowserHighRisk.Kind.FILE, BrowserHighRisk.forClick("a", "", "Download")?.kind)
        assertEquals(BrowserHighRisk.Kind.PUBLISH, BrowserHighRisk.forClick("button", "submit", "Continue")?.kind)
        assertEquals(BrowserHighRisk.Kind.PASSWORD, BrowserHighRisk.forClick("input", "password", "")?.kind)
    }
}
