package xyz.kurdcloud.englishmaster.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.AuthSession

class SupabaseClientTest {
    private val server = MockWebServer()
    private val sessions = FakeSessions()
    private lateinit var services: SupabaseServices

    private val nowSeconds get() = System.currentTimeMillis() / 1000

    @Before
    fun setUp() {
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        // MockWebServer is plain HTTP; SupabaseConfig.isConfigured requires https, so build directly.
        services = NetworkFactory.create(SupabaseConfig(url.replace("http://", "https://"), "anon-key"), sessions,
            okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
            }.build())
    }

    @After
    fun tearDown() = server.close()

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val tokenJson = """{"access_token":"new-access","token_type":"bearer","expires_in":3600,
        "refresh_token":"new-refresh","user":{"id":"u1","email":"a@b.co"}}"""

    @Test
    fun `sign in sends api key and parses session`() = runTest {
        server.enqueue(json(200, tokenJson))
        val result = authCall { services.auth.signIn(PasswordGrantRequest("a@b.co", "pw123456")) }
        val token = (result as ApiResult.Success).value
        assertEquals("u1", token.user.id)
        val recorded = server.takeRequest()
        assertEquals("anon-key", recorded.headers["apikey"])
        assertTrue(recorded.url.toString().contains("grant_type=password"))
    }

    @Test
    fun `invalid credentials are mapped`() = runTest {
        server.enqueue(json(400, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
        val result = authCall { services.auth.signIn(PasswordGrantRequest("a@b.co", "wrong")) }
        assertEquals(ApiError.Auth(AuthFailure.INVALID_CREDENTIALS), (result as ApiResult.Failure).error)
    }

    @Test
    fun `postgrest sqlstate codes are mapped`() = runTest {
        sessions.session = session(expiresIn = 3600)
        val cases = listOf(
            json(400, """{"code":"22023","message":"question not available"}""") to ApiError.Validation("22023"),
            json(403, """{"code":"42501","message":"account suspended"}""") to ApiError.Forbidden,
            json(500, """{"code":"54000","message":"rate limit exceeded"}""") to ApiError.RateLimited,
            json(503, "upstream down") to ApiError.Server(503),
        )
        cases.forEach { (response, expected) ->
            server.enqueue(response)
            val result = restCall { services.practice.getMyPracticeSummary() }
            assertEquals(expected, (result as ApiResult.Failure).error)
        }
    }

    @Test
    fun `rest calls carry the bearer token`() = runTest {
        sessions.session = session(expiresIn = 3600)
        server.enqueue(json(200, "[]"))
        restCall { services.practice.getMyPracticeSummary() }
        assertEquals("Bearer old-access", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 triggers one refresh and the request is retried`() = runTest {
        sessions.session = session(expiresIn = 3600)
        server.enqueue(json(401, """{"code":"PGRST303","message":"JWT expired"}"""))
        server.enqueue(json(200, tokenJson))
        server.enqueue(json(200, """[{"cefr_level":"A1","answered":3,"correct":2}]"""))

        val result = restCall { services.practice.getMyPracticeSummary() }

        assertEquals(3L, (result as ApiResult.Success).value.single().answered)
        assertEquals("new-access", sessions.session?.accessToken)
        server.takeRequest()
        val refresh = server.takeRequest()
        assertTrue(refresh.url.toString().contains("grant_type=refresh_token"))
        assertTrue(refresh.body!!.utf8().contains("old-refresh"))
        assertEquals("Bearer new-access", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `rejected refresh token clears the session`() = runTest {
        sessions.session = session(expiresIn = 3600)
        server.enqueue(json(401, """{"code":"PGRST303","message":"JWT expired"}"""))
        server.enqueue(json(400, """{"error_code":"refresh_token_not_found"}"""))

        val result = restCall { services.practice.getMyPracticeSummary() }

        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
        assertNull(sessions.session)
        assertTrue(sessions.clearedAsExpired)
    }

    @Test
    fun `token about to expire is refreshed before the request`() = runTest {
        sessions.session = session(expiresIn = 10)
        server.enqueue(json(200, tokenJson))
        server.enqueue(json(200, "[]"))

        restCall { services.practice.getMyPracticeSummary() }

        assertTrue(server.takeRequest().url.toString().contains("grant_type=refresh_token"))
        assertEquals("Bearer new-access", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `connection failure is a retryable network error`() = runTest {
        sessions.session = session(expiresIn = 3600)
        server.close()
        val result = restCall { services.practice.getMyPracticeSummary() }
        val error = (result as ApiResult.Failure).error
        assertEquals(ApiError.Network, error)
        assertTrue(error.isRetryable)
    }

    private fun session(expiresIn: Long) =
        AuthSession("u1", "a@b.co", "old-access", "old-refresh", nowSeconds + expiresIn)

    private class FakeSessions : SessionProvider {
        @Volatile var session: AuthSession? = null
        var clearedAsExpired = false
        override fun current() = session
        override fun update(session: AuthSession) { this.session = session }
        override fun clear(expired: Boolean) { session = null; clearedAsExpired = expired }
    }
}
