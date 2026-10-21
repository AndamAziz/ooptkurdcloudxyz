package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailLinkTest {
    @Test
    fun resetAndConfirmationLinksCarryTokens() {
        val reset = EmailLink.parse("app.englishmaster://auth-callback#access_token=a.b.c&expires_at=1&expires_in=3600&refresh_token=r1&token_type=bearer&type=recovery")
        assertEquals(EmailLink.SignedIn("a.b.c", "r1", 3600, recovery = true), reset)
        val signup = EmailLink.parse("app.englishmaster://auth-callback#access_token=x&refresh_token=y&expires_in=60&type=signup")
        assertEquals(EmailLink.SignedIn("x", "y", 60, recovery = false), signup)
    }

    @Test
    fun expiredOrBrokenLinksAreRecognised() {
        assertEquals(EmailLink.Expired, EmailLink.parse(
            "app.englishmaster://auth-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"))
        assertEquals(EmailLink.Invalid, EmailLink.parse("app.englishmaster://auth-callback#error=server_error"))
        assertEquals(EmailLink.Invalid, EmailLink.parse("app.englishmaster://auth-callback"))
        assertNull(EmailLink.parse("https://example.com/#access_token=a&refresh_token=b"))
    }
}
