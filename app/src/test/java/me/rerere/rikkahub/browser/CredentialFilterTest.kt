package me.rerere.rikkahub.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialFilterTest {
    @Test
    fun filtersJwtAndPrefixedKeys() {
        val result = CredentialFilter.filter(
            "jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature " +
                "key=sk-abcdefghijklmnopqrstuvwxyz123456"
        )

        assertEquals(2, result.hitCount)
        assertTrue(result.text.contains("[已过滤:"))
        assertTrue(!result.text.contains("eyJhbGci"))
        assertTrue(!result.text.contains("sk-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun filtersValidCardButLeavesInvalidCard() {
        val valid = "4532015112830366"
        val invalid = "4532015112830367"
        val result = CredentialFilter.filter("valid=$valid invalid=$invalid")

        assertEquals(1, result.hitCount)
        assertTrue(result.text.contains(invalid))
        assertTrue(!result.text.contains(valid))
    }

    @Test
    fun longNormalWordIsNotFiltered() {
        val word = "supercalifragilisticexpialidocioussupercalifragilistic"
        val result = CredentialFilter.filter(word)

        assertEquals(0, result.hitCount)
        assertEquals(word, result.text)
    }
}
