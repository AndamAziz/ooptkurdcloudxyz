package xyz.kurdcloud.englishmaster.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.kurdcloud.englishmaster.core.database.EnglishMasterDatabase
import xyz.kurdcloud.englishmaster.core.database.LevelPackEntity
import xyz.kurdcloud.englishmaster.core.database.QuestionEntity
import xyz.kurdcloud.englishmaster.core.database.ResponseEntity
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.SyncReport.StopReason
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.Topic
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig
import xyz.kurdcloud.englishmaster.core.network.SupabaseServices

@RunWith(RobolectricTestRunner::class)
class SyncAndPracticeTest {
    private val server = MockWebServer()
    private val sessions = FakeSessions(AuthSession("u1", "a@b.co", "access", "refresh", Long.MAX_VALUE / 2))
    private val online = MutableStateFlow(true)
    private val network = object : NetworkMonitor { override val isOnline = online }
    private var syncRequests = 0
    private lateinit var db: EnglishMasterDatabase
    private lateinit var services: SupabaseServices
    private lateinit var syncer: ResponseSyncer
    private lateinit var practice: OfflineFirstPracticeRepository
    private var nextId = 0

    @Before
    fun setUp() {
        server.start()
        db = EnglishMasterDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val url = server.url("/").toString().trimEnd('/').replace("http://", "https://")
        val toHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
        }.build()
        services = NetworkFactory.create(SupabaseConfig(url, "anon"), sessions, toHttp)
        syncer = ResponseSyncer(services.practice, db.responses(), sessions)
        practice = OfflineFirstPracticeRepository(
            db.responses(), syncer, { syncRequests++ }, network, newId = { "r${++nextId}" },
        )
        runBlocking {
            db.content().replaceLevel("A1", listOf(question("q1"), question("q2"), question("q3")), LevelPackEntity("A1", 3, 0))
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun mistakesAreQuestionsWhoseLatestMarkedAnswerWasWrong() = runTest {
        val r = db.responses()
        // q1: wrong then right -> not a mistake. q2: right then wrong -> mistake. q3: wrong but still pending -> not counted.
        r.insert(ResponseEntity("a1", "u1", "att", "q1", "A1", "A", 1, SyncState.SYNCED.name, isCorrect = false))
        r.insert(ResponseEntity("a2", "u1", "att", "q1", "A1", "B", 2, SyncState.SYNCED.name, isCorrect = true))
        r.insert(ResponseEntity("a3", "u1", "att", "q2", "A1", "B", 1, SyncState.SYNCED.name, isCorrect = true))
        r.insert(ResponseEntity("a4", "u1", "att", "q2", "A1", "A", 2, SyncState.SYNCED.name, isCorrect = false))
        r.insert(ResponseEntity("a5", "u1", "att", "q3", "A1", "A", 1, SyncState.PENDING.name))
        r.insert(ResponseEntity("a6", "u2", "att2", "q1", "A1", "A", 3, SyncState.SYNCED.name, isCorrect = false))
        val content = OfflineFirstContentRepository(services.practice, db.questions(), db.levelPacks(), db.content())

        assertEquals(listOf("q2"), content.pickMistakes(CefrLevel.A1, "u1", 10).map { it.id })
        assertEquals(mapOf(CefrLevel.A1 to 1), content.observeMistakeCounts("u1").first())
        assertEquals(mapOf(CefrLevel.A1 to 1), content.observeMistakeCounts("u2").first())
    }

    @Test
    fun downloadedPicturesAreSavedForOfflineUse() = runTest {
        val dir = ApplicationProvider.getApplicationContext<Context>().filesDir.resolve("media-test").apply { deleteRecursively() }
        val base = server.url("/storage/v1/object/public/question-media/").toString()
        val media = MediaFiles(OkHttpClient(), base, dir)
        val content = OfflineFirstContentRepository(services.practice, db.questions(), db.levelPacks(), db.content(), media = media)
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(
                """[{"id":"p1","cefr_level":"A1","skill":"vocabulary","prompt":"What time is it?","options":["three","four"],
                "version":1,"review_status":"approved","updated_at":"t","media_type":"image","media_path":"samples/clock.png",
                "media_caption":"A clock","passage":null},
                {"id":"p2","cefr_level":"A1","skill":"reading","prompt":"Where?","options":["a","b"],"version":1,
                "review_status":"approved","updated_at":"t","passage":"Ali lives in Duhok.","tags":["kurdistan","story"]}]""",
            ).build(),
        )
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body("PNGDATA").build())

        assertEquals(ApiResult.Success(2), content.downloadLevel(CefrLevel.A1))

        assertEquals("/rest/v1/rpc/get_practice_questions_v3", server.takeRequest().url.encodedPath)
        assertEquals("/storage/v1/object/public/question-media/samples/clock.png", server.takeRequest().url.encodedPath)
        val pic = content.questions(listOf("p1")).single().media!!
        assertEquals(base + "samples/clock.png", pic.url)
        assertEquals("PNGDATA", java.io.File(pic.localPath!!).readText())
        assertEquals("Ali lives in Duhok.", content.questions(listOf("p2")).single().passage)
        assertEquals(listOf("kurdistan", "story"), db.questions().byId("p2")!!.tags)
        dir.deleteRecursively()
    }

    @Test
    fun topicsMatchWholeTagsPassagesAndMediaAcrossLevels() = runTest {
        val content = OfflineFirstContentRepository(services.practice, db.questions(), db.levelPacks(), db.content())
        db.content().replaceLevel(
            "A1",
            listOf(
                question("uk").copy(tags = listOf("life_in_uk", "history")),
                question("ku").copy(tags = listOf("kurdistan")),
                question("read").copy(passage = "Hana lives in Erbil."),
                question("pic").copy(mediaType = "image", mediaPath = "samples/flag.png"),
                // "life_in_uk_extra" must not match the whole tag "life_in_uk".
                question("other").copy(tags = listOf("grammar", "life_in_uk_extra")),
            ),
            LevelPackEntity("A1", 5, 1),
        )
        db.content().replaceLevel("B2", listOf(question("uk2").copy(cefrLevel = "B2", tags = listOf("life_in_uk"))), LevelPackEntity("B2", 1, 1))

        val counts = content.observeTopicCounts().first()
        assertEquals(mapOf(Topic.LIFE_IN_UK to 2, Topic.KURDISTAN to 1, Topic.STORIES to 1, Topic.WORLD to 1, Topic.PICTURES to 1), counts)
        assertEquals(setOf("uk", "uk2"), content.pickTopic(Topic.LIFE_IN_UK, "u1", 10).map { it.id }.toSet())
        assertEquals(setOf(CefrLevel.A1, CefrLevel.B2), content.pickTopic(Topic.LIFE_IN_UK, "u1", 10).map { it.level }.toSet())
        assertEquals(listOf("read"), content.pickTopic(Topic.STORIES, "u1", 10).map { it.id })
        assertEquals(listOf("pic"), content.pickTopic(Topic.PICTURES, "u1", 10).map { it.id })
        assertEquals(1, content.pickTopic(Topic.LIFE_IN_UK, "u1", 1).size)
    }

    private fun question(id: String) =
        QuestionEntity(id, "A1", "grammar", "Prompt $id", listOf("a", "b", "c", "d"), 1, "unreviewed", "t", 0)

    private suspend fun model(id: String) = db.questions().byId(id)!!.toModel()

    private fun verdict(responseId: String, correct: Boolean, duplicate: Boolean = false) = MockResponse.Builder()
        .code(200).addHeader("Content-Type", "application/json")
        .body("""[{"response_id":"$responseId","is_correct":$correct,"correct_option":"B","explanation":"why","received_at":"2026-10-19T10:00:00Z","duplicate":$duplicate}]""")
        .build()

    private fun error(code: Int, sqlState: String) = MockResponse.Builder()
        .code(code).addHeader("Content-Type", "application/json").body("""{"code":"$sqlState","message":"x"}""").build()

    @Test
    fun onlineAnswerIsScoredByServerImmediately() = runTest {
        server.enqueue(verdict("r1", correct = true))

        val answer = practice.answer("u1", "att-1", model("q1"), optionIndex = 1)

        assertEquals(SyncState.SYNCED, answer.syncState)
        assertEquals(true, answer.isCorrect)
        assertEquals("B", answer.correctOption)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"p_response_id\":\"r1\""))
        assertTrue(body.contains("\"p_chosen_option\":\"B\""))
        assertEquals(0, syncRequests)
    }

    @Test
    fun offlineAnswerIsSavedPendingAndSyncScheduled() = runTest {
        online.value = false

        val answer = practice.answer("u1", "att-1", model("q1"), optionIndex = 0)

        assertEquals(SyncState.PENDING, answer.syncState)
        assertNull(answer.isCorrect)
        assertEquals(1, syncRequests)
        assertEquals(0, server.requestCount)
        assertEquals(1, practice.observePendingCount("u1").first())
    }

    @Test
    fun retryAfterLostResponseReusesIdempotencyKeyAndNeverDuplicates() = runTest {
        // First upload: connection drops before a response arrives.
        server.enqueue(MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.ShutdownConnection).build())
        val first = practice.answer("u1", "att-1", model("q1"), optionIndex = 1)
        assertEquals(SyncState.PENDING, first.syncState)
        assertEquals(1, syncRequests)

        // Retry: the server already stored it and reports a duplicate.
        server.enqueue(verdict("r1", correct = true, duplicate = true))
        val report = syncer.syncAll()

        assertEquals(1, report.synced)
        assertEquals(0, report.stillPending)
        server.takeRequest()
        assertTrue(server.takeRequest().body!!.utf8().contains("\"p_response_id\":\"r1\""))
        assertEquals(listOf("r1"), db.responses().observeAttempt("att-1").first().map { it.responseId })
        assertEquals(1, db.responses().byId("r1")!!.syncAttempts)
    }

    @Test
    fun syncContinuesPastRejectedItemsAndStopsOnOutage() = runTest {
        online.value = false
        listOf("q1", "q2", "q3").forEach { practice.answer("u1", "att-1", model(it), 0) }

        server.enqueue(verdict("r1", correct = false))
        server.enqueue(error(400, "22023"))     // q2 withdrawn on the server: permanent rejection
        server.enqueue(error(503, "PGRST000"))  // outage: keep for later
        val report = syncer.syncAll()

        assertEquals(1, report.synced)
        assertEquals(1, report.failed)
        assertEquals(1, report.stillPending)
        assertEquals(StopReason.SERVER_UNAVAILABLE, report.stoppedReason)
        assertEquals(SyncState.FAILED.name, db.responses().byId("r2")!!.syncState)
        assertEquals(SyncState.PENDING.name, db.responses().byId("r3")!!.syncState)
    }

    @Test
    fun suspendedAccountStopsSyncWithoutDiscardingAnswers() = runTest {
        online.value = false
        practice.answer("u1", "att-1", model("q1"), 0)
        server.enqueue(error(403, "42501"))

        val report = syncer.syncAll()

        assertEquals(StopReason.ACCOUNT_RESTRICTED, report.stoppedReason)
        assertEquals(1, report.stillPending)
    }

    @Test
    fun onlyTheSignedInUsersAnswersAreUploaded() = runTest {
        db.responses().insert(ResponseEntity("other", "u2", "att-9", "q1", "A1", "A", 0, SyncState.PENDING.name))
        val report = syncer.syncAll()
        assertEquals(0, report.synced)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun noSessionMeansNothingIsSent() = runTest {
        sessions.session = null
        assertEquals(StopReason.NOT_SIGNED_IN, syncer.syncAll().stoppedReason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun workerRetriesWhileOfflineAndSucceedsOnceDrained() = runTest {
        online.value = false
        practice.answer("u1", "att-1", model("q1"), 0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = SyncWorkerFactory(syncer = { syncer })

        server.enqueue(MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.ShutdownConnection).build())
        val retry = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()
        assertEquals(ListenableWorker.Result.retry(), retry)

        server.enqueue(verdict("r1", correct = true))
        val done = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()
        assertEquals(ListenableWorker.Result.success(), done)
        assertEquals(0, practice.observePendingCount("u1").first())
    }

    @Test
    fun downloadPaginatesAndStoresQuestionsWithoutAnswerKeys() = runTest {
        val content = OfflineFirstContentRepository(services.practice, db.questions(), db.levelPacks(), db.content())
        val page1 = (1..100).joinToString(",", "[", "]") { qJson("b%03d".format(it)) }
        val page2 = (101..120).joinToString(",", "[", "]") { qJson("b%03d".format(it)) }
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(page1).build())
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(page2).build())

        val progress = mutableListOf<Int>()
        val result = content.downloadLevel(CefrLevel.B1) { progress += it }

        assertEquals(120, (result as ApiResult.Success).value)
        assertEquals(listOf(100, 120), progress)
        server.takeRequest()
        assertTrue(server.takeRequest().body!!.utf8().contains("\"p_after\":\"b100\""))
        assertEquals(120, content.observePacks().first().single { it.level == CefrLevel.B1 }.questionCount)
    }

    @Test
    fun failedDownloadLeavesExistingPackUntouched() = runTest {
        val content = OfflineFirstContentRepository(services.practice, db.questions(), db.levelPacks(), db.content())
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
            .body((1..100).joinToString(",", "[", "]") { qJson("n$it") }).build())
        server.enqueue(error(503, "PGRST000"))

        val result = content.downloadLevel(CefrLevel.A1)

        assertTrue(result is ApiResult.Failure)
        assertEquals(3, content.observePacks().first().single().questionCount)
        assertEquals("Prompt q1", db.questions().byId("q1")?.prompt)
    }

    private fun qJson(id: String) =
        """{"id":"$id","cefr_level":"B1","skill":"grammar","prompt":"P","options":["a","b","c","d"],"version":1,"review_status":"unreviewed","updated_at":"t"}"""

    private class FakeSessions(var session: AuthSession?) : SessionProvider {
        override fun current() = session
        override fun update(session: AuthSession) { this.session = session }
        override fun clear(expired: Boolean) { session = null }
    }
}
