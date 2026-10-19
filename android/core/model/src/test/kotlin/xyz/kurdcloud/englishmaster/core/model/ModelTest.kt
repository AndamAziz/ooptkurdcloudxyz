package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun `option letters map both ways`() {
        assertEquals("A", optionLetter(0))
        assertEquals("D", optionLetter(3))
        assertEquals(2, optionIndex("c"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `option index beyond F is rejected`() {
        optionLetter(6)
    }

    @Test
    fun `cefr levels round-trip through api values`() {
        CefrLevel.entries.forEach { assertEquals(it, CefrLevel.fromApi(it.apiValue)) }
    }

    @Test
    fun `credential rules`() {
        assertTrue(CredentialRules.isValidEmail("learner@example.com"))
        assertFalse(CredentialRules.isValidEmail("learner@"))
        assertFalse(CredentialRules.isValidEmail("two words@example.com"))
        assertTrue(CredentialRules.isValidPassword("abcdef12"))
        assertFalse(CredentialRules.isValidPassword("abcdefgh"))
        assertFalse(CredentialRules.isValidPassword("abc12"))
        assertFalse(CredentialRules.isValidDisplayName("   "))
        assertTrue(CredentialRules.isValidDisplayName("ئاراس"))
    }

    @Test
    fun `session expiry window and redacted toString`() {
        val s = AuthSession("u1", "a@b.co", "secret-access", "secret-refresh", expiresAtEpochSeconds = 1_000)
        assertTrue(s.expiresWithin(60, nowEpochSeconds = 950))
        assertFalse(s.expiresWithin(60, nowEpochSeconds = 900))
        assertFalse(s.toString().contains("secret"))
    }
}
