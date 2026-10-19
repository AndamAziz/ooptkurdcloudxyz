package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ReviewState
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig

class StaffRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: SupabaseStaffRepository

    @Before
    fun setUp() {
        server.start()
        val url = server.url("/").toString().trimEnd('/').replace("http://", "https://")
        val toHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
        }.build()
        val sessions = object : SessionProvider {
            override fun current() = AuthSession("admin", "a@b.co", "token", "refresh", Long.MAX_VALUE / 2)
            override fun update(session: AuthSession) = Unit
            override fun clear(expired: Boolean) = Unit
        }
        repo = SupabaseStaffRepository(NetworkFactory.create(SupabaseConfig(url, "anon"), sessions, toHttp).staff)
    }

    @After fun tearDown() = server.close()

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun accessIsDerivedFromServerRoles() = runTest {
        server.enqueue(json(200, """["student","teacher"]"""))
        assertEquals(ApiResult.Success(StaffAccess(canReviewQuestions = true, canManageUsers = false)), repo.myAccess())
    }

    @Test
    fun usersAreMappedAndSearchIsSent() = runTest {
        server.enqueue(json(200, """[{"user_id":"u1","email":"s@x.co","display_name":"Sara","roles":["student","teacher"],
            "account_status":"suspended","created_at":"t","answered":4,"correct":3,"last_answer_at":null}]"""))

        val users = (repo.users("  sar ") as ApiResult.Success).value

        assertEquals(setOf(Role.STUDENT, Role.TEACHER), users.single().roles)
        assertTrue(users.single().suspended)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"p_search\":\"sar\""))
    }

    @Test
    fun forbiddenIsReportedNotThrown() = runTest {
        server.enqueue(json(403, """{"code":"42501","message":"insufficient role"}"""))
        assertEquals(ApiResult.Failure(ApiError.Forbidden), repo.users(null))
    }

    @Test
    fun studentRoleIsNeverSentForChange() = runTest {
        assertEquals(ApiResult.Failure(ApiError.Validation("22023")), repo.setRole("u1", Role.STUDENT, false))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun roleChangeReturnsServerRoles() = runTest {
        server.enqueue(json(200, """["student","teacher"]"""))
        assertEquals(ApiResult.Success(setOf(Role.STUDENT, Role.TEACHER)), repo.setRole("u1", Role.TEACHER, true))
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"p_role\":\"teacher\"") && body.contains("\"p_granted\":true"))
    }

    @Test
    fun suspendSendsStatusAndParsesScalarResult() = runTest {
        server.enqueue(json(200, "\"suspended\""))
        assertEquals(ApiResult.Success(true), repo.setSuspended("u1", true))
    }

    @Test
    fun questionsAreFilteredByLevelAndReviewIsMapped() = runTest {
        server.enqueue(json(200, """[{"id":"q1","cefr_level":"B1","prompt":"P","options":["a","b"],"correct_option":"B",
            "explanation":null,"status":"published","review_status":"unreviewed"}]"""))
        assertEquals("B", (repo.questions(CefrLevel.B1) as ApiResult.Success).value.single().correctOption)
        assertTrue(server.takeRequest().url.toString().contains("cefr_level=eq.B1"))

        server.enqueue(json(200, """[{"id":"q1","review_status":"approved","status":"retired"}]"""))
        assertEquals(ApiResult.Success(ReviewState("approved", "retired")), repo.review("q1", approved = true, status = "retired"))
        assertTrue(server.takeRequest().body!!.utf8().contains("\"p_status\":\"retired\""))
    }

    @Test
    fun activityDetailOmitsInternalIds() = runTest {
        server.enqueue(json(200, """[{"occurred_at":"2026-10-19T20:00:00Z","event_type":"admin.role_granted","actor_id":"a",
            "actor_name":"Andam","entity_type":"profile","entity_id":"u","metadata":{"role":"teacher","attempt_id":"x"}}]"""))
        val entry = (repo.recentActivity() as ApiResult.Success).value.single()
        assertEquals("role: teacher", entry.detail)
        assertEquals("Andam", entry.actorName)
    }
}
