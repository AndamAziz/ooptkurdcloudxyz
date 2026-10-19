package xyz.kurdcloud.englishmaster.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.AuthSession

/**
 * Opt-in check of the real client against a live Supabase project. Creates no data.
 * Run with: SUPABASE_LIVE_URL=https://<ref>.supabase.co SUPABASE_LIVE_KEY=<publishable key> ./gradlew :core:network:test
 */
class LiveSupabaseSmokeTest {
    private val url = System.getenv("SUPABASE_LIVE_URL").orEmpty()
    private val key = System.getenv("SUPABASE_LIVE_KEY").orEmpty()

    private object NoSession : SessionProvider {
        override fun current(): AuthSession? = null
        override fun update(session: AuthSession) = Unit
        override fun clear(expired: Boolean) = Unit
    }

    private fun services(): SupabaseServices {
        assumeTrue("set SUPABASE_LIVE_URL and SUPABASE_LIVE_KEY to run", url.isNotEmpty() && key.isNotEmpty())
        return NetworkFactory.create(SupabaseConfig(url, key), NoSession)
    }

    @Test
    fun unknownAccountIsReportedAsInvalidCredentials() = runTest {
        val result = authCall { services().auth.signIn(PasswordGrantRequest("nobody@example.invalid", "wrongpass1")) }
        assertEquals(ApiError.Auth(AuthFailure.INVALID_CREDENTIALS), (result as ApiResult.Failure).error)
    }

    @Test
    fun practiceQuestionsRequireASignedInUser() = runTest {
        val result = restCall { services().practice.getPracticeQuestions(PracticeQuestionsRequest("A1", null, 10)) }
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
    }
}
