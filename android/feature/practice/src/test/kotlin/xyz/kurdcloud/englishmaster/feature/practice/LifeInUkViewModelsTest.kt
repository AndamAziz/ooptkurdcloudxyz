package xyz.kurdcloud.englishmaster.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LifeInUkChapter
import xyz.kurdcloud.englishmaster.core.model.LifeInUkTest

@OptIn(ExperimentalCoroutinesApi::class)
class LifeInUkViewModelsTest {
    private val scheduler = TestCoroutineScheduler()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    private val questions = (1..30).map { question("l$it") } // option B (index 1) is correct in the fakes

    private fun mockTest(network: FakeNetwork = FakeNetwork(), practice: FakePractice = FakePractice(network)) =
        MockTestViewModel("u1", FakeContent(questions), practice, clock = { scheduler.currentTime })

    @Test
    fun `a mock test has the official format and is marked at the end`() {
        val vm = mockTest()
        val s = vm.uiState.value
        assertEquals(MockPhase.RUNNING, s.phase)
        assertEquals(LifeInUkTest.QUESTIONS, s.total)
        assertEquals(LifeInUkTest.MINUTES * 60L, s.remainingSeconds)

        // 20 right, 3 wrong, 1 left out; answers can be changed before handing in.
        repeat(24) { i ->
            vm.goTo(i)
            when {
                i < 20 -> { vm.select(0); vm.select(1) }
                i < 23 -> vm.select(2)
            }
        }
        assertEquals(23, vm.uiState.value.answeredCount)
        assertTrue(vm.uiState.value.marked.isEmpty()) // no feedback during the test

        vm.submit()
        val r = vm.uiState.value
        assertEquals(MockPhase.RESULT, r.phase)
        assertEquals(20, r.correct)
        assertTrue(r.passed)
        assertEquals(83, r.percent)
        vm.select(0) // locked after handing in
        assertEquals(23, vm.uiState.value.choices.size)
    }

    @Test
    fun `fewer than 18 correct is a fail`() {
        val vm = mockTest()
        repeat(24) { i -> vm.goTo(i); vm.select(if (i < 17) 1 else 0) }
        vm.submit()
        assertEquals(17, vm.uiState.value.correct)
        assertFalse(vm.uiState.value.passed)
    }

    @Test
    fun `the test is handed in automatically when the time runs out`() {
        val vm = mockTest()
        vm.select(1)
        scheduler.advanceTimeBy(LifeInUkTest.MINUTES * 60_000L - 2_000)
        assertEquals(MockPhase.RUNNING, vm.uiState.value.phase)
        scheduler.advanceTimeBy(3_000)
        val s = vm.uiState.value
        assertEquals(MockPhase.RESULT, s.phase)
        assertTrue(s.autoSubmitted)
        assertEquals(1, s.correct)
        assertEquals(0L, s.remainingSeconds)
    }

    @Test
    fun `offline answers wait and the result completes after sync`() {
        val network = FakeNetwork(online = false)
        val practice = FakePractice(network)
        val vm = mockTest(network, practice)
        repeat(24) { i -> vm.goTo(i); vm.select(1) }
        vm.submit()
        assertEquals(24, vm.uiState.value.waiting)
        assertEquals(0, vm.uiState.value.correct)

        practice.completeSync()
        assertEquals(0, vm.uiState.value.waiting)
        assertEquals(24, vm.uiState.value.correct)
        assertTrue(vm.uiState.value.passed)
    }

    @Test
    fun `too few downloaded questions means no mock test`() {
        val vm = MockTestViewModel("u1", FakeContent(questions.take(10)), FakePractice(FakeNetwork()), clock = { scheduler.currentTime })
        assertEquals(MockPhase.NOT_ENOUGH, vm.uiState.value.phase)
        assertEquals(10, vm.uiState.value.available)
    }

    @Test
    fun `the course page downloads the pack and counts questions per chapter`() = runTest(scheduler) {
        val history = questions.take(5)
        val content = FakeContent().apply { taggedQuestions = mapOf(LifeInUkChapter.HISTORY.tag to history) }
        val vm = LifeInUkViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(null, vm.uiState.value.pack)
        vm.download()
        val s = vm.uiState.value
        assertEquals(CefrLevel.LIFE_IN_UK, s.pack?.level)
        assertEquals(5, s.chapters[LifeInUkChapter.HISTORY])
        assertEquals(0, s.chapters[LifeInUkChapter.VALUES])
        assertEquals(null, s.downloading)
        assertFalse(s.canTakeMockTest) // the fake pack has 3 questions
    }

    @Test
    fun `downloading needs a connection`() = runTest(scheduler) {
        val vm = LifeInUkViewModel("u1", FakeContent(), FakePractice(FakeNetwork()), FakeNetwork(online = false))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.download()
        assertEquals(R.string.practice_download_needs_internet, vm.uiState.value.message?.text)
        assertEquals(null, vm.uiState.value.pack)
    }

    @Test
    fun `chapter practice uses only that chapter's questions`() {
        val content = FakeContent(questions).apply { taggedQuestions = mapOf(LifeInUkChapter.GOVERNMENT.tag to listOf(question("gov1"))) }
        val vm = PracticeViewModel(null, "u1", content, FakePractice(FakeNetwork()), tag = LifeInUkChapter.GOVERNMENT.tag)
        assertEquals(listOf("gov1"), vm.uiState.value.questions.map { it.id })
    }
}
