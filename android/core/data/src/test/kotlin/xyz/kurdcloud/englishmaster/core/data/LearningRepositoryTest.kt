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
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig

class LearningRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: SupabaseLearningRepository
    private lateinit var api: xyz.kurdcloud.englishmaster.core.network.LearningApi

    @Before
    fun setUp() {
        server.start()
        val url = server.url("/").toString().trimEnd('/').replace("http://", "https://")
        val toHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
        }.build()
        val sessions = object : SessionProvider {
            override fun current() = AuthSession("u1", "a@b.co", "token", "refresh", Long.MAX_VALUE / 2)
            override fun update(session: AuthSession) = Unit
            override fun clear(expired: Boolean) = Unit
        }
        api = NetworkFactory.create(SupabaseConfig(url, "anon"), sessions, toHttp).learning
        repo = SupabaseLearningRepository(api)
    }

    @After fun tearDown() = server.close()

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun overviewIsMappedWithTimestamps() = runTest {
        server.enqueue(json(200, """[{"cefr_level":"B1","published":41,"answered":22,"correct":14,"required":30,"required_percent":70,
            "exam_unlocked":false,"exam_available_at":"2026-10-21T03:00:00+00:00","exam_in_progress":null,"last_exam_score":12,
            "last_exam_total":20,"certificate_code":null,"certificate_issued_at":null}]"""))

        val o = (repo.overview() as ApiResult.Success).value.single()

        assertEquals(CefrLevel.B1, o.level)
        assertEquals(8, o.remainingToUnlock)
        assertEquals(1792551600000L, o.examAvailableAtMillis)
        assertEquals(false, o.examInProgress)
        assertEquals("/rest/v1/rpc/get_learning_overview_v2", server.takeRequest().url.encodedPath)
    }

    @Test
    fun placementStatusSaysWhenTheNextAttemptOpens() = runTest {
        server.enqueue(json(200, """[{"attempts_left":0,"next_attempt_at":"2026-10-21T03:00:00+00:00","in_progress":false}]"""))

        val status = (repo.placementStatus() as ApiResult.Success).value

        assertEquals(0, status.attemptsLeft)
        assertEquals(1792551600000L, status.nextAttemptAtMillis)
        assertEquals(false, status.inProgress)
        assertEquals("/rest/v1/rpc/get_placement_status_v2", server.takeRequest().url.encodedPath)
    }

    @Test
    fun passedExamWaitingForApprovalIsNotACertificateYet() = runTest {
        server.enqueue(json(200, """[{"cefr_level":"A2","published":40,"answered":30,"correct":28,"required":30,"required_percent":70,
            "exam_unlocked":true,"exam_available_at":null,"exam_in_progress":null,"last_exam_score":18,
            "last_exam_total":20,"certificate_code":null,"certificate_issued_at":"2026-10-20T03:00:00+00:00","certificate_status":"pending"}]"""))

        val o = (repo.overview() as ApiResult.Success).value.single()

        assertEquals(true, o.certificatePending)
        assertEquals(xyz.kurdcloud.englishmaster.core.model.LevelStage.AWAITING_APPROVAL, o.stage(0))
    }

    @Test
    fun examIsStartedAndSubmittedWithLetterAnswers() = runTest {
        server.enqueue(json(200, """[{"attempt_id":"att","cefr_level":"A1","expires_at":"2026-10-20T03:30:00.5+00:00",
            "questions":[{"id":"q1","cefr_level":"A1","prompt":"He ___ happy.","options":["are","is","am","be"]}]}]"""))
        server.enqueue(json(200, """[{"score":1,"total":1,"passed":true,"pass_percent":80,"certificate_code":"EM-ABCD-EFGH-JKLM",
            "next_attempt_at":null,"expired":false}]"""))

        val session = (repo.startLevelExam(CefrLevel.A1) as ApiResult.Success).value
        assertEquals(ExamKind.LEVEL_FINAL, session.kind)
        assertEquals("is", session.questions.single().options[1])
        assertTrue(server.takeRequest().body!!.utf8().contains(""""p_cefr_level":"A1""""))

        val result = (repo.submitLevelExam("att", mapOf("q1" to "B")) as ApiResult.Success).value
        assertEquals(true, result.passed)
        assertEquals("EM-ABCD-EFGH-JKLM", result.certificateCode)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains(""""p_answers":{"q1":"B"}"""))
    }

    @Test
    fun businessRefusalsKeepTheServerHint() = runTest {
        server.enqueue(json(400, """{"code":"P0001","message":"next attempt not yet available","hint":"retry_later"}"""))
        assertEquals(ApiResult.Failure(ApiError.Validation("P0001", "retry_later")), repo.startLevelExam(CefrLevel.A1))
    }

    @Test
    fun placementAndCertificates() = runTest {
        server.enqueue(json(200, """[{"score":6,"total":18,"passed_level":"A2","recommended_level":"B1"}]"""))
        server.enqueue(json(200, """[{"cefr_level":"A1","holder_name":"Sara Ahmed","score":18,"total":20,
            "verification_code":"EM-ABCD-EFGH-JKLM","issued_at":"2026-10-20T03:00:00+00:00"}]"""))

        val placement = (repo.submitPlacement("att", emptyMap()) as ApiResult.Success).value
        assertEquals(CefrLevel.A2, placement.passedLevel)
        assertEquals(CefrLevel.B1, placement.recommendedLevel)
        assertEquals("Sara Ahmed", (repo.certificates() as ApiResult.Success).value.single().holderName)
    }

    @Test
    fun deletingTheAccountWipesThisDeviceOnlyAfterTheServerConfirms() = runTest {
        val auth = object : AuthRepository {
            var signedOut = false
            override val session = kotlinx.coroutines.flow.MutableStateFlow<xyz.kurdcloud.englishmaster.core.model.AuthSession?>(null)
            override val sessionExpired = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
            override suspend fun signIn(email: String, password: String) = ApiResult.Success(Unit)
            override suspend fun register(displayName: String, email: String, password: String) = ApiResult.Success(RegisterOutcome.SIGNED_IN)
            override suspend fun requestPasswordReset(email: String) = ApiResult.Success(Unit)
            override suspend fun signOut() { signedOut = true }
        }
        var cleared = 0
        val account = SupabaseAccountRepository(api, auth) { cleared++ }

        server.enqueue(json(400, """{"code":"P0001","message":"staff accounts are removed by an admin","hint":"staff_account"}"""))
        val refused = account.deleteAccount() as ApiResult.Failure
        assertEquals("staff_account", (refused.error as xyz.kurdcloud.englishmaster.core.network.ApiError.Validation).hint)
        assertEquals(0, cleared)
        assertEquals(false, auth.signedOut)
        assertEquals("/rest/v1/rpc/delete_my_account", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Success(Unit), account.deleteAccount())
        assertEquals(1, cleared)
        assertEquals(true, auth.signedOut)
    }
}
