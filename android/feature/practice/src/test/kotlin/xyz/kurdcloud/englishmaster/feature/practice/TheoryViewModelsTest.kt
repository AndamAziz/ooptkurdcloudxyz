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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem

@OptIn(ExperimentalCoroutinesApi::class)
class TheoryViewModelsTest {
    private val scheduler = TestCoroutineScheduler()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    private val alertness = (1..6).map { theoryQuestion("a$it") }
    private val sections = listOf(
        TheorySection("alertness", "Alertness", "ئاگاداری", published = 6, answered = 3, correct = 2),
        TheorySection("attitude", "Attitude", "هەڵسوکەوت", published = 0, answered = 0, correct = 0),
    )

    private fun theory() = FakeTheory(mapOf("alertness" to alertness), sections)

    @Test
    fun `the chapter list shows what is attempted, and lists chapters with no questions`() = runTest {
        val repo = theory()
        val vm = TheoryViewModel(repo, FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value

        assertEquals(2, state.sections.size)
        val first = state.sections.first()
        assertEquals("3 of 6", "${first.answered} of ${first.published}")
        assertEquals(66, first.accuracyPercent)
        assertEquals(50, first.answeredPercent)
        // An empty chapter stays on the list, so learners see the whole test from day one.
        assertTrue(state.sections[1].isEmpty)
        // Both chapters with questions are still missing from the device.
        assertEquals(listOf("alertness"), state.missing.map { it.slug })
    }

    @Test
    fun `downloading saves every chapter that has questions`() = runTest {
        val repo = theory()
        val vm = TheoryViewModel(repo, FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.downloadAll()

        assertEquals(emptyList<TheorySection>(), vm.uiState.value.missing)
        assertNull(vm.uiState.value.downloading)
        assertEquals(R.plurals.theory_download_done, vm.uiState.value.message?.text)
        assertEquals(6, vm.uiState.value.message?.arg)
    }

    @Test
    fun `downloading offline asks for a connection instead of failing silently`() = runTest {
        val vm = TheoryViewModel(theory(), FakeNetwork(online = false))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.downloadAll()

        assertEquals(R.string.practice_download_needs_internet, vm.uiState.value.message?.text)
    }

    @Test
    fun `a study card is revealed before the learner says whether they knew it`() = runTest {
        val repo = theory()
        repo.downloadSection("alertness")
        val vm = TheorySessionViewModel("alertness", TheoryMode.STUDY, repo)

        assertEquals(TheoryPhase.RUNNING, vm.uiState.value.phase)
        assertEquals(6, vm.uiState.value.total)
        assertFalse(vm.uiState.value.revealed)

        vm.reveal()
        assertTrue(vm.uiState.value.revealed)
        vm.knew(true)

        // Recorded as right, and the next card starts face down.
        assertEquals(listOf(Triple("a1", true, TheoryMode.STUDY)), repo.recorded)
        assertEquals(1, vm.uiState.value.index)
        assertFalse(vm.uiState.value.revealed)
        assertEquals(1, vm.uiState.value.correct)
    }

    @Test
    fun `a chapter is studied in full, however many questions it has`() = runTest {
        // The first chapter of the real course has 26; a round used to stop at 20.
        val big = (1..26).map { theoryQuestion("b$it", section = "big") }
        val repo = FakeTheory(mapOf("big" to big), listOf(TheorySection("big", "Big", null, 26, 0, 0)))
        repo.downloadSection("big")

        assertEquals(26, TheorySessionViewModel("big", TheoryMode.STUDY, repo).uiState.value.total)
        assertEquals(26, TheorySessionViewModel("big", TheoryMode.TEST, repo).uiState.value.total)
    }

    @Test
    fun `a test question borrows its wrong options from the same chapter`() = runTest {
        val repo = theory()
        repo.downloadSection("alertness")
        val vm = TheorySessionViewModel("alertness", TheoryMode.TEST, repo)
        val item = vm.uiState.value.item!!

        assertEquals(TheoryTestItem.OPTIONS, item.options.size)
        assertEquals(item.question.answer, item.options[item.correctIndex])
        assertEquals(item.options.size, item.options.distinct().size)
        // Every option is some question's answer, so none of them reads as made up.
        assertTrue(item.options.all { option -> alertness.any { it.answer == option } })
        // The Sorani text follows each option, in the same order.
        assertEquals(item.options.map { "وەڵامی ${it.removePrefix("Answer ")}" }, item.optionsCkb)
    }

    @Test
    fun `a wrong choice is marked at once and the round ends with a score`() = runTest {
        val repo = theory()
        repo.downloadSection("alertness")
        val vm = TheorySessionViewModel("alertness", TheoryMode.TEST, repo)

        val item = vm.uiState.value.item!!
        val wrong = (0 until item.options.size).first { it != item.correctIndex }
        vm.choose(wrong)
        assertEquals(wrong, vm.uiState.value.chosen)
        assertEquals(0, vm.uiState.value.correct)
        // A second tap cannot change an answer that is already recorded.
        vm.choose(item.correctIndex)
        assertEquals(1, repo.recorded.size)
        assertEquals(Triple(item.question.id, false, TheoryMode.TEST), repo.recorded.single())

        vm.next()
        repeat(5) {
            val current = vm.uiState.value.item ?: return@repeat
            vm.choose(current.correctIndex)
            vm.next()
        }
        assertEquals(TheoryPhase.DONE, vm.uiState.value.phase)
        assertEquals(5, vm.uiState.value.correct)
        assertEquals(83, vm.uiState.value.percent)
    }

    @Test
    fun `a chapter the device does not have is downloaded when it is opened`() = runTest {
        val repo = theory()
        val vm = TheorySessionViewModel("alertness", TheoryMode.STUDY, repo)

        assertEquals(TheoryPhase.RUNNING, vm.uiState.value.phase)
        assertEquals(6, vm.uiState.value.total)
    }

    @Test
    fun `a chapter with no questions says so instead of showing an empty card`() = runTest {
        val vm = TheorySessionViewModel("attitude", TheoryMode.TEST, theory())

        assertEquals(TheoryPhase.EMPTY, vm.uiState.value.phase)
    }
}
