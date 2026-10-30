package xyz.kurdcloud.englishmaster.core.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.kurdcloud.englishmaster.core.database.EnglishMasterDatabase
import xyz.kurdcloud.englishmaster.core.database.TheoryQuestionEntity
import xyz.kurdcloud.englishmaster.core.database.TheoryResponseEntity
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.model.TheoryTest
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig

/**
 * The paper a mock exam builds: every chapter represented, the whole chapter available to study,
 * and the questions the learner is weakest on favoured.
 */
@RunWith(RobolectricTestRunner::class)
class TheoryMockTest {
    private lateinit var db: EnglishMasterDatabase
    private lateinit var theory: OfflineFirstTheoryRepository

    /** Ten chapters of twelve questions: more than a paper needs, like the real course. */
    private val chapters = (1..10).map { c -> "chapter-$c" to (1..12).map { q -> "c${c}q$q" } }

    @Before
    fun setUp() {
        db = EnglishMasterDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val services = NetworkFactory.create(
            SupabaseConfig("https://example.invalid", "anon"),
            // The paper is built from what is on the device, so the network is never reached.
            object : SessionProvider {
                override fun current(): AuthSession? = null
                override fun update(session: AuthSession) = Unit
                override fun clear(expired: Boolean) = Unit
            },
        )
        theory = OfflineFirstTheoryRepository(
            services.practice,
            db.theory(),
            object : NetworkMonitor { override val isOnline = MutableStateFlow(false) },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        db.theory().upsertQuestions(
            chapters.flatMap { (slug, ids) ->
                ids.mapIndexed { i, id ->
                    TheoryQuestionEntity(
                        id = id,
                        sectionSlug = slug,
                        sortOrder = i,
                        prompt = "Question $id?",
                        answer = "Answer $id",
                        version = 1,
                        downloadedAtMillis = 0,
                    )
                }
            },
        )
    }

    private suspend fun answer(questionId: String, section: String, correct: Boolean, at: Long) =
        db.theory().insertResponse(
            TheoryResponseEntity("r-$questionId-$at", questionId, section, correct, "test", at),
        )

    @Test
    fun `a paper is the official size and takes the same number from every chapter`() = runTest {
        seed()
        val paper = theory.mockTest()

        assertEquals(TheoryTest.QUESTIONS, paper.size)
        val perChapter = paper.groupingBy { it.question.sectionSlug }.eachCount()
        assertEquals(chapters.size, perChapter.size)
        assertTrue("every chapter is represented: $perChapter", perChapter.values.all { it == TheoryTest.PER_SECTION })
        // No question is asked twice in one sitting.
        assertEquals(paper.size, paper.map { it.question.id }.distinct().size)
    }

    @Test
    fun `each question comes with four options, the right one among them`() = runTest {
        seed()
        theory.mockTest().forEach { item ->
            assertEquals(4, item.options.size)
            assertEquals(item.question.answer, item.options[item.correctIndex])
            assertEquals(item.options.size, item.options.distinct().size)
            // Wrong options are borrowed from the question's own chapter, so they stay plausible.
            val chapter = chapters.first { it.first == item.question.sectionSlug }.second
            assertTrue(item.options.all { option -> chapter.any { "Answer $it" == option } })
        }
    }

    @Test
    fun `questions the learner got wrong come back before ones they know`() = runTest {
        seed()
        // In chapter 1, eight questions are already answered right and two wrong.
        (1..8).forEach { answer("c1q$it", "chapter-1", correct = true, at = 100) }
        (9..10).forEach { answer("c1q$it", "chapter-1", correct = false, at = 100) }

        val chapterOne = theory.mockTest().filter { it.question.sectionSlug == "chapter-1" }.map { it.question.id }

        // The two never answered and the two wrong are the weakest four; all must be on the paper.
        assertTrue("picked $chapterOne", chapterOne.containsAll(listOf("c1q11", "c1q12", "c1q9", "c1q10")))
    }

    @Test
    fun `the newest answer decides, so a question since learnt stops coming first`() = runTest {
        seed()
        (1..12).forEach { answer("c1q$it", "chapter-1", correct = true, at = 100) }
        // One was got wrong first but answered right afterwards: it counts as known.
        answer("c1q1", "chapter-1", correct = false, at = 50)

        val weakest = theory.mockTest().filter { it.question.sectionSlug == "chapter-1" }.map { it.question.id }
        assertEquals(TheoryTest.PER_SECTION, weakest.size)
    }

    @Test
    fun `a short course still fills the paper from the chapters it has`() = runTest {
        // Two chapters only: five each is ten, so the rest is topped up from what is left.
        db.theory().upsertQuestions(
            (1..2).flatMap { c ->
                (1..40).map { q ->
                    TheoryQuestionEntity(
                        id = "s${c}q$q",
                        sectionSlug = "short-$c",
                        sortOrder = q,
                        prompt = "Question $q?",
                        answer = "Answer s${c}q$q",
                        version = 1,
                        downloadedAtMillis = 0,
                    )
                }
            },
        )
        val paper = theory.mockTest()

        assertEquals(TheoryTest.QUESTIONS, paper.size)
        assertEquals(paper.size, paper.map { it.question.id }.distinct().size)
    }

    @Test
    fun `a chapter is studied and tested in full, not in rounds of twenty`() = runTest {
        seed()
        // 26 questions, the size of the first chapter of the real course.
        db.theory().upsertQuestions(
            (1..26).map { q ->
                TheoryQuestionEntity(
                    id = "bigq$q",
                    sectionSlug = "big",
                    sortOrder = q,
                    prompt = "Question $q?",
                    answer = "Answer big $q",
                    version = 1,
                    downloadedAtMillis = 0,
                )
            },
        )
        assertEquals(26, theory.deck("big").size)
        assertEquals(26, theory.testRound("big").size)
    }

    @Test
    fun `nothing is built when no chapter has been downloaded`() = runTest {
        assertEquals(emptyList<Any>(), theory.mockTest())
        assertEquals(0, theory.savedQuestionCount())
    }
}
