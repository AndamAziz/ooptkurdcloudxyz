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
import xyz.kurdcloud.englishmaster.core.model.CertificateRequestStatus
import xyz.kurdcloud.englishmaster.core.model.DeleteOutcome
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.NewQuestion
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
        repo = SupabaseStaffRepository(NetworkFactory.create(SupabaseConfig(url, "anon"), sessions, toHttp).staff, newId = { "id1" })
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

    @Test
    fun questionsAreEditedAndDeleted() = runTest {
        server.enqueue(json(200, "3"))
        val edit = NewQuestion(CefrLevel.A1, "vocabulary", " What is the code? ", listOf("837631", "837613"), 0, " ", null,
            MediaType.IMAGE, "staff/x.png", "Kurdistan IPTV", publish = false)
        assertEquals(ApiResult.Success(3), repo.updateQuestion("q1", edit))
        val update = server.takeRequest()
        assertEquals("/rest/v1/rpc/staff_update_question", update.url.encodedPath)
        val body = update.body!!.utf8()
        assertTrue(body, body.contains(""""p_question_id":"q1"""") && body.contains(""""p_prompt":"What is the code?"""") &&
            body.contains(""""p_correct_option":"A"""") && body.contains(""""p_media_path":"staff/x.png"""") && !body.contains("p_publish"))

        server.enqueue(json(200, "\"archived\""))
        assertEquals(ApiResult.Success(DeleteOutcome.ARCHIVED), repo.deleteQuestion("q1"))
        assertEquals("/rest/v1/rpc/staff_delete_question", server.takeRequest().url.encodedPath)

        server.enqueue(json(200, "[]"))
        repo.questions(CefrLevel.A1)
        assertTrue(server.takeRequest().url.toString().contains("deleted_at=is.null"))
    }

    @Test
    fun certificateRequestsAreListedAndReviewed() = runTest {
        server.enqueue(json(200, """[{"id":"c1","user_id":"u1","email":"s@x.co","display_name":"Sara","cefr_level":"A2","score":18,
            "total":20,"requested_at":"2026-10-20T03:00:00+00:00","status":"pending","reviewed_at":null,"reviewer_name":null}]"""))
        val request = (repo.certificateRequests(CertificateRequestStatus.PENDING) as ApiResult.Success).value.single()
        assertEquals(CefrLevel.A2, request.level)
        assertEquals("Sara", request.displayName)
        val list = server.takeRequest()
        assertEquals("/rest/v1/rpc/staff_list_certificates", list.url.encodedPath)
        assertTrue(list.body!!.utf8().contains(""""p_status":"pending""""))

        server.enqueue(json(200, """[{"status":"approved","verification_code":"EM-AAAA-BBBB-CCCC"}]"""))
        assertEquals(ApiResult.Success(CertificateRequestStatus.APPROVED), repo.reviewCertificate("c1", approve = true, note = "  "))
        val review = server.takeRequest()
        assertEquals("/rest/v1/rpc/staff_review_certificate", review.url.encodedPath)
        val body = review.body!!.utf8()
        assertTrue(body, body.contains(""""p_certificate_id":"c1"""") && body.contains(""""p_approve":true""") && !body.contains("p_note"))

        server.enqueue(json(400, """{"code":"P0001","message":"you cannot review your own certificate","hint":"own_certificate"}"""))
        val own = repo.reviewCertificate("c2", approve = false, note = "x") as ApiResult.Failure
        assertEquals("own_certificate", (own.error as ApiError.Validation).hint)
    }

    @Test
    fun namesQuestionsMediaAndBulkPublish() = runTest {
        server.enqueue(json(200, "\"Sara Ahmed\""))
        assertEquals(ApiResult.Success("Sara Ahmed"), repo.setDisplayName("u1", "  Sara Ahmed "))
        val rename = server.takeRequest()
        assertEquals("/rest/v1/rpc/staff_set_display_name", rename.url.encodedPath)
        assertTrue(rename.body!!.utf8().contains(""""p_display_name":"Sara Ahmed""""))
        assertEquals(ApiResult.Failure(ApiError.Validation("22023")), repo.setDisplayName("u1", "   "))

        server.enqueue(json(200, """{"Key":"question-media/staff/id1.png"}"""))
        assertEquals(ApiResult.Success("staff/id1.png"), repo.uploadMedia(byteArrayOf(1, 2, 3), "image/png"))
        val upload = server.takeRequest()
        assertEquals("/storage/v1/object/question-media/staff/id1.png", upload.url.encodedPath)
        assertEquals("image/png", upload.headers["Content-Type"])
        assertEquals("Bearer token", upload.headers["Authorization"])
        assertEquals(ApiResult.Failure(ApiError.Validation("unsupported_media")), repo.uploadMedia(byteArrayOf(1), "image/gif"))

        server.enqueue(json(200, "\"new-id\""))
        val created = repo.createQuestion(
            NewQuestion(CefrLevel.A2, "reading", " Where is Ali? ", listOf("Erbil", "Duhok"), 1, "", "Ali lives in Duhok.",
                MediaType.IMAGE, "staff/id1.png", "A map", publish = false),
        )
        assertEquals(ApiResult.Success("new-id"), created)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains(""""p_correct_option":"B"""") && body.contains(""""p_media_type":"image"""") &&
            body.contains(""""p_publish":false""") && !body.contains("p_explanation"))

        server.enqueue(json(200, "7"))
        assertEquals(ApiResult.Success(7), repo.publishDrafts(CefrLevel.B1))
        assertEquals("/rest/v1/rpc/staff_publish_drafts", server.takeRequest().url.encodedPath)
    }
}
