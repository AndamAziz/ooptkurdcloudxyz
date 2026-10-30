package xyz.kurdcloud.englishmaster.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTest

/** The mock exam: one paper across every chapter, on the clock, marked only when handed in. */
@OptIn(ExperimentalCoroutinesApi::class)
class TheoryMockViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    /** Ten chapters of six questions, as the real course has. */
    private val chapters = (1..10).associate { c ->
        "chapter-$c" to (1..6).map { q -> theoryQuestion("c${c}q$q", section = "chapter-$c") }
    }
    private val sections = chapters.keys.map { TheorySection(it, it, null, published = 6, answered = 0, correct = 0) }

    private fun theory() = FakeTheory(chapters, sections)

    private fun TestCoroutineScheduler.clock(): () -> Long = { currentTime }

    @Test
    fun `a paper covers every chapter and downloads what the device is missing`() = runTest {
        val repo = theory()
        val vm = TheoryMockViewModel(repo, testScheduler.clock())

        val state = vm.uiState.value
        assertEquals(TheoryMockPhase.RUNNING, state.phase)
        assertEquals(TheoryTest.QUESTIONS, state.total)
        // Five from each of the ten chapters, nothing asked twice.
        assertEquals(TheoryTest.QUESTIONS, state.items.map { it.question.id }.distinct().size)
        assertEquals(
            chapters.keys.associateWith { TheoryTest.PER_SECTION },
            state.items.groupingBy { it.question.sectionSlug }.eachCount(),
        )
        // The standard paper: 57 minutes, pass mark 43.
        assertEquals(TheoryTest.MINUTES * 60L, state.remainingSeconds)
        assertEquals(TheoryTest.PASS_MARK, state.passMark)
    }

    @Test
    fun `nothing is marked until the paper is handed in, and an answer can be changed`() = runTest {
        val repo = theory()
        val vm = TheoryMockViewModel(repo, testScheduler.clock())
        val first = vm.uiState.value.items.first()

        val wrong = (0 until first.options.size).first { it != first.correctIndex }
        vm.select(wrong)
        vm.select(first.correctIndex)
        assertEquals(first.correctIndex, vm.uiState.value.choices[first.question.id])
        // Still running, and nothing has reached the learner's record.
        assertEquals(TheoryMockPhase.RUNNING, vm.uiState.value.phase)
        assertTrue(repo.recorded.isEmpty())
    }

    @Test
    fun `answering everything correctly is a pass, and every answer is recorded`() = runTest {
        val repo = theory()
        val vm = TheoryMockViewModel(repo, testScheduler.clock())

        repeat(vm.uiState.value.total) { i ->
            vm.goTo(i)
            vm.select(vm.uiState.value.item!!.correctIndex)
        }
        vm.submit()

        val state = vm.uiState.value
        assertEquals(TheoryMockPhase.RESULT, state.phase)
        assertEquals(TheoryTest.QUESTIONS, state.correct)
        assertEquals(100, state.percent)
        assertTrue(state.passed)
        assertFalse(state.autoSubmitted)
        assertTrue(state.wrong.isEmpty())
        assertEquals(TheoryTest.QUESTIONS, repo.recorded.size)
        assertTrue(repo.recorded.all { it.second && it.third == TheoryMode.TEST })
    }

    @Test
    fun `a question left blank is wrong but is not held against the chapter`() = runTest {
        val repo = theory()
        val vm = TheoryMockViewModel(repo, testScheduler.clock())

        // Ten answered correctly, the other forty left blank.
        repeat(10) { i ->
            vm.goTo(i)
            vm.select(vm.uiState.value.item!!.correctIndex)
        }
        vm.submit()

        val state = vm.uiState.value
        assertEquals(10, state.correct)
        assertEquals(40, state.unanswered)
        assertFalse(state.passed)
        assertEquals(40, state.wrong.size)
        // A question never attempted would misrepresent the chapter, so it is not recorded.
        assertEquals(10, repo.recorded.size)
    }

    @Test
    fun `when the time runs out the paper is handed in automatically`() = runTest {
        val repo = theory()
        val vm = TheoryMockViewModel(repo, testScheduler.clock())
        vm.select(vm.uiState.value.item!!.correctIndex)

        advanceTimeBy(TheoryTest.MINUTES * 60_000L + 1_000L)

        val state = vm.uiState.value
        assertEquals(TheoryMockPhase.RESULT, state.phase)
        assertTrue(state.autoSubmitted)
        assertEquals(0L, state.remainingSeconds)
        assertEquals(1, state.correct)
    }

    @Test
    fun `the clock counts down while the paper is being sat`() = runTest {
        val vm = TheoryMockViewModel(theory(), testScheduler.clock())
        // advanceTimeBy stops just short of the last millisecond, so the tick at 61s has not run.
        advanceTimeBy(61_000L)
        assertEquals(TheoryTest.MINUTES * 60L - 60, vm.uiState.value.remainingSeconds)
    }

    @Test
    fun `a shorter paper gets a pass mark in proportion`() = runTest {
        // Two chapters only: ten questions, so the pass mark scales from 43 in 50.
        val two = chapters.entries.take(2).associate { it.key to it.value }
        val vm = TheoryMockViewModel(
            FakeTheory(two, sections.take(2)),
            testScheduler.clock(),
        )

        val state = vm.uiState.value
        assertEquals(10, state.total)
        assertEquals(9, state.passMark)
        // And proportionally less time, so the pace is the same.
        assertEquals(12 * 60L, state.remainingSeconds)
    }

    @Test
    fun `sitting another paper starts a fresh clock`() = runTest {
        val vm = TheoryMockViewModel(theory(), testScheduler.clock())
        repeat(vm.uiState.value.total) { i ->
            vm.goTo(i)
            vm.select(vm.uiState.value.item!!.correctIndex)
        }
        vm.submit()
        assertEquals(TheoryMockPhase.RESULT, vm.uiState.value.phase)

        vm.again()
        val state = vm.uiState.value
        assertEquals(TheoryMockPhase.RUNNING, state.phase)
        assertEquals(TheoryTest.QUESTIONS, state.total)
        assertTrue(state.choices.isEmpty())
        assertEquals(0, state.index)
        assertEquals(TheoryTest.MINUTES * 60L, state.remainingSeconds)
    }
}
