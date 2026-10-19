package xyz.kurdcloud.englishmaster.core.database

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var db: EnglishMasterDatabase

    @Before
    fun setUp() {
        db = EnglishMasterDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private fun question(id: String, level: String = "A1") =
        QuestionEntity(id, level, "grammar", "Prompt $id", listOf("a", "b", "c", "d"), 1, "unreviewed", "2026-10-19T00:00:00Z", 0)

    private fun response(id: String, questionId: String, user: String = "u1", state: String = "PENDING") =
        ResponseEntity(id, user, "attempt-1", questionId, "A1", "A", answeredAtMillis = id.hashCode().toLong(), syncState = state)

    @Test
    fun replaceLevelSwapsContentAtomicallyAndKeepsOptions() = runTest {
        db.content().replaceLevel("A1", listOf(question("q1"), question("q2")), LevelPackEntity("A1", 2, 1))
        db.content().replaceLevel("A1", listOf(question("q3")), LevelPackEntity("A1", 1, 2))
        db.content().replaceLevel("B1", listOf(question("b1", "B1")), LevelPackEntity("B1", 1, 2))

        assertNull(db.questions().byId("q1"))
        assertEquals(listOf("a", "b", "c", "d"), db.questions().byId("q3")?.options)
        assertEquals(listOf("A1" to 1, "B1" to 1), db.levelPacks().observeAll().first().map { it.cefrLevel to it.questionCount })
    }

    @Test
    fun sessionPicksPreferUnansweredQuestions() = runTest {
        db.content().replaceLevel("A1", (1..5).map { question("q$it") }, LevelPackEntity("A1", 5, 1))
        listOf("q1", "q2", "q3").forEachIndexed { i, q -> db.responses().insert(response("r$i", q)) }

        val picked = db.questions().pickForSession("A1", "u1", limit = 2).map { it.id }.toSet()

        assertEquals(setOf("q4", "q5"), picked)
    }

    @Test
    fun pendingQueueIsPerUserAndSyncUpdatesState() = runTest {
        db.responses().insert(response("r1", "q1"))
        db.responses().insert(response("r2", "q2"))
        db.responses().insert(response("r3", "q3", user = "u2"))

        assertEquals(2, db.responses().observePendingCount("u1").first())

        db.responses().markSynced("r1", isCorrect = true, correctOption = "A", explanation = null, syncedAt = 10)
        db.responses().markFailed("r2", "validation")

        assertEquals(0, db.responses().observePendingCount("u1").first())
        assertEquals(listOf("r3"), db.responses().pending("u2", 10).map { it.responseId })
        assertEquals(listOf(LevelProgressRow("A1", answered = 1, correct = 1)), db.responses().observeProgress("u1").first())
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun duplicateResponseIdIsRejectedLocally() = runTest {
        db.responses().insert(response("r1", "q1"))
        db.responses().insert(response("r1", "q2"))
    }

    @Test
    fun syncedVerdictIsNotOverwritten() = runTest {
        db.responses().insert(response("r1", "q1"))
        db.responses().markSynced("r1", true, "A", null, 1)
        db.responses().markSynced("r1", false, "B", null, 2)
        assertEquals(true, db.responses().byId("r1")?.isCorrect)
    }
}
