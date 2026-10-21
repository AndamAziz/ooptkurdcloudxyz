package xyz.kurdcloud.englishmaster.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.kurdcloud.englishmaster.core.model.EmailLink
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.AuthFailure
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionAndAuthTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()

    /** Robolectric has no AndroidKeyStore; XOR keeps the ciphertext different from the plaintext. */
    private val fakeCipher = object : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun dataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("session.preferences_pb") }

    private val session = AuthSession("u1", "a@b.co", "access-token-value", "refresh-token-value", 2_000_000_000)

    @Test
    fun sessionSurvivesRestartAndIsNotStoredInPlaintext() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val ds = dataStore(scope)
        SessionStore(ds, fakeCipher).save(session)

        val raw = folder.root.resolve("session.preferences_pb").readBytes().decodeToString()
        assertFalse(raw.contains("refresh-token-value"))

        // A new store over the same file (as after a process restart) restores the session.
        val restarted = SessionStore(ds, fakeCipher)
        assertNull(restarted.current())
        restarted.load()
        assertEquals(session, restarted.current())
    }

    @Test
    fun undecryptableSessionSignsOutCleanly() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val ds = dataStore(scope)
        ds.edit { it[stringPreferencesKey("session")] = "not-valid-ciphertext" }
        val store = SessionStore(ds, fakeCipher)

        store.load()

        assertNull(store.current())
        assertNull(ds.data.first()[stringPreferencesKey("session")])
    }

    private fun repo(scope: CoroutineScope): Pair<SupabaseAuthRepository, SessionStore> {
        val store = SessionStore(dataStore(scope), fakeCipher)
        val url = server.url("/").toString().trimEnd('/').replace("http://", "https://")
        val config = SupabaseConfig(url, "anon")
        val toHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
        }.build()
        return SupabaseAuthRepository(config, NetworkFactory.create(config, store, toHttp).auth, store) to store
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun registerWithEmailConfirmationDoesNotCreateSession() = runTest {
        val (repo, store) = repo(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)))
        server.enqueue(json(200, """{"id":"u1","email":"a@b.co","confirmation_sent_at":"2026-10-19T10:00:00Z"}"""))

        assertEquals(ApiResult.Success(RegisterOutcome.CONFIRM_EMAIL), repo.register("Ali", "a@b.co", "abcdef12"))
        assertNull(store.current())
        val body = server.takeRequest().body!!.utf8()
        assertEquals(true, body.contains("\"display_name\":\"Ali\""))
        assertFalse("client must never send a role", body.contains("role"))
    }

    @Test
    fun emailLinksOpenTheAppAndAResetLinkLetsTheUserSetANewPassword() = runTest {
        val (repo, store) = repo(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)))

        // Reset and confirmation emails point back into the app.
        server.enqueue(json(200, "{}"))
        repo.requestPasswordReset("a@b.co")
        assertEquals(EmailLink.REDIRECT, server.takeRequest().url.queryParameter("redirect_to"))
        server.enqueue(json(200, """{"id":"u1","email":"a@b.co"}"""))
        repo.register("Ali", "a@b.co", "abcdef12")
        assertEquals(EmailLink.REDIRECT, server.takeRequest().url.queryParameter("redirect_to"))

        // The link's tokens become a session once the server confirms whose they are.
        val link = EmailLink.parse("${EmailLink.REDIRECT}#access_token=AT&expires_in=3600&refresh_token=RT&token_type=bearer&type=recovery")
            as EmailLink.SignedIn
        server.enqueue(json(200, """{"id":"u7","email":"a@b.co"}"""))
        assertEquals(ApiResult.Success(Unit), repo.completeEmailLink(link))
        val user = server.takeRequest()
        assertEquals("/auth/v1/user", user.url.encodedPath)
        assertEquals("Bearer AT", user.headers["Authorization"])
        assertEquals("u7", store.current()?.userId)
        assertEquals("RT", store.current()?.refreshToken)

        server.enqueue(json(200, """{"id":"u7","email":"a@b.co"}"""))
        assertEquals(ApiResult.Success(Unit), repo.updatePassword("newpass99"))
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("Bearer AT", update.headers["Authorization"])
        assertEquals("""{"password":"newpass99"}""", update.body!!.utf8())

        server.enqueue(json(422, """{"error_code":"same_password","msg":"New password should be different"}"""))
        assertEquals(ApiResult.Failure(ApiError.Auth(AuthFailure.SAME_PASSWORD)), repo.updatePassword("newpass99"))
    }

    @Test
    fun signInStoresSessionAndSignOutClearsIt() = runTest {
        val (repo, store) = repo(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)))
        server.enqueue(json(200, """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u1","email":"a@b.co"}}"""))
        assertEquals(ApiResult.Success(Unit), repo.signIn(" a@b.co ", "abcdef12"))
        assertEquals("u1", store.current()?.userId)

        server.enqueue(MockResponse.Builder().code(204).build())
        repo.signOut()
        assertNull(store.current())
        server.takeRequest()
        assertEquals("Bearer a", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun wrongPasswordIsReportedWithoutStoringAnything() = runTest {
        val (repo, store) = repo(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)))
        server.enqueue(json(400, """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
        assertEquals(ApiResult.Failure(ApiError.Auth(AuthFailure.INVALID_CREDENTIALS)), repo.signIn("a@b.co", "bad"))
        assertNull(store.current())
    }

    @Test
    fun unconfiguredBuildFailsFastWithoutNetwork() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val store = SessionStore(dataStore(scope), fakeCipher)
        val config = SupabaseConfig("", "")
        val repo = SupabaseAuthRepository(config, NetworkFactory.create(config, store).auth, store)
        assertEquals(ApiResult.Failure(ApiError.NotConfigured), repo.signIn("a@b.co", "abcdef12"))
    }
}
