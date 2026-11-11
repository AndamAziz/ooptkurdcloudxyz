package xyz.kurdcloud.englishmaster.core.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import xyz.kurdcloud.englishmaster.core.database.TheorySectionEntity
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
    @Test
    fun `a question with its own options is shown with exactly those`() = runTest {
        db.theory().upsertQuestions(
            listOf(
                TheoryQuestionEntity(
                    id = "own1", sectionSlug = "docs", sortOrder = 1,
                    prompt = "What does third-party insurance cover?",
                    answer = "Damage to other vehicles",
                    options = listOf("Damage to your vehicle", "Damage to other vehicles",
                                     "Injury to yourself", "All damage and injury"),
                    optionsCkb = listOf("أ", "ب", "ج", "د"),
                    correctOption = 1,
                    version = 1, downloadedAtMillis = 0,
                ),
            ),
        )
        val item = theory.testRound("docs").single()

        assertEquals(4, item.options.size)
        assertEquals("Damage to other vehicles", item.options[item.correctIndex])
        // Exactly the official four, whatever order they were shuffled into.
        assertEquals(
            listOf("All damage and injury", "Damage to other vehicles", "Damage to your vehicle", "Injury to yourself"),
            item.options.sorted(),
        )
        // The Sorani text follows its own option through the shuffle.
        assertEquals("ب", item.optionsCkb[item.correctIndex])
    }

    @Test
    fun `two questions sharing an answer are told apart by their own options`() = runTest {
        // Both of these are in the official test and both are answered "Damage to other vehicles".
        db.theory().upsertQuestions(
            listOf(
                TheoryQuestionEntity(
                    id = "p1", sectionSlug = "docs", sortOrder = 1,
                    prompt = "What does third-party insurance cover?", answer = "Damage to other vehicles",
                    options = listOf("Damage to your vehicle", "Damage to other vehicles",
                                     "Injury to yourself", "All damage and injury"),
                    correctOption = 1, version = 1, downloadedAtMillis = 0,
                ),
                TheoryQuestionEntity(
                    id = "p2", sectionSlug = "docs", sortOrder = 2,
                    prompt = "What's covered by third-party insurance?", answer = "Damage to other vehicles",
                    options = listOf("Damage to your vehicle", "Fire damage to your vehicle",
                                     "Flood damage to your vehicle", "Damage to other vehicles"),
                    correctOption = 3, version = 1, downloadedAtMillis = 0,
                ),
            ),
        )
        val (first, second) = theory.testRound("docs").sortedBy { it.question.id }

        assertEquals(first.question.answer, second.question.answer)
        assertTrue("the wrong options must differ", first.options.sorted() != second.options.sorted())
        assertEquals("Damage to other vehicles", first.options[first.correctIndex])
        assertEquals("Damage to other vehicles", second.options[second.correctIndex])
    }

    @Test
    fun `a question without options still borrows them from its chapter`() = runTest {
        seed()
        val item = theory.testRound("chapter-1").first()
        assertEquals(4, item.options.size)
        assertEquals(item.question.answer, item.options[item.correctIndex])
    }

    /**
     * A chapter is behind when the device holds fewer questions than it publishes, and also when
     * the chapter changed after the device saved it: a corrected question does not move the
     * count, so without the second rule a correction would never reach anyone.
     */
    @Test
    fun `a chapter is behind when it gains a question and when one of its questions is corrected`() = runTest {
        seed()
        val saved = 5_000L
        db.theory().upsertQuestions(
            db.theory().questionsOf("chapter-1").map { it.copy(downloadedAtMillis = saved) },
        )
        fun section(published: Int, changedAt: Long) =
            TheorySectionEntity("chapter-1", 1, "Chapter 1", null, published, 0, 0, fetchedAtMillis = saved, changedAtMillis = changedAt)

        // Held in full and unchanged since it was saved.
        db.theory().upsertSections(listOf(section(published = 12, changedAt = saved - 1)))
        assertEquals(emptySet<String>(), theory.observeChaptersBehind().first())

        // A question was added to it.
        db.theory().upsertSections(listOf(section(published = 13, changedAt = saved - 1)))
        assertEquals(setOf("chapter-1"), theory.observeChaptersBehind().first())

        // Same count, but one of its questions was corrected after the device saved the chapter.
        db.theory().upsertSections(listOf(section(published = 12, changedAt = saved + 1)))
        assertEquals(setOf("chapter-1"), theory.observeChaptersBehind().first())
    }
}
