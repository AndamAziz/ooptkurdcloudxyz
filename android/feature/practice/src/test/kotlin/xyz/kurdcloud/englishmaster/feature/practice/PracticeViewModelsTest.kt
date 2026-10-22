package xyz.kurdcloud.englishmaster.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.SyncReport
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.Topic

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `online answers are scored and the session summary counts them`() {
        val network = FakeNetwork(online = true)
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"), question("q2"))), FakePractice(network))
        assertEquals(2, vm.uiState.value.questions.size)

        vm.submit() // nothing selected: ignored
        assertNull(vm.uiState.value.currentAnswer)

        vm.select(1); vm.submit()
        assertEquals(true, vm.uiState.value.currentAnswer?.isCorrect)
        vm.select(3) // locked after answering
        assertEquals(1, vm.uiState.value.selected)
        vm.next()
        vm.select(0); vm.submit()
        vm.next()

        assertTrue(vm.uiState.value.finished)
        assertEquals(SessionSummary(total = 2, correct = 1, incorrect = 1, pending = 0), vm.uiState.value.summary)
    }

    @Test
    fun `offline answers stay pending and update when background sync lands`() {
        val network = FakeNetwork(online = false)
        val practice = FakePractice(network)
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), practice)

        vm.select(1); vm.submit()
        assertEquals(SyncState.PENDING, vm.uiState.value.currentAnswer?.syncState)
        assertEquals(1, vm.uiState.value.summary.pending)

        practice.completeSync()
        assertEquals(SyncState.SYNCED, vm.uiState.value.currentAnswer?.syncState)
        assertEquals(1, vm.uiState.value.summary.correct)
    }

    @Test
    fun `topic session draws its questions from the topic across levels`() {
        val uk = listOf(question("uk1"), question("uk2").copy(level = CefrLevel.B2))
        val content = FakeContent(listOf(question("other"))).apply { topicQuestions = mapOf(Topic.LIFE_IN_UK to uk) }
        val vm = PracticeViewModel(null, "u1", content, FakePractice(FakeNetwork()), topic = Topic.LIFE_IN_UK)
        assertEquals(listOf("uk1", "uk2"), vm.uiState.value.questions.map { it.id })

        val empty = PracticeViewModel(null, "u1", content, FakePractice(FakeNetwork()), topic = Topic.BRITAIN)
        assertTrue(empty.uiState.value.isEmpty)
    }

    @Test
    fun `level without downloaded questions shows the empty state`() {
        val vm = PracticeViewModel(CefrLevel.B2, "u1", FakeContent(emptyList()), FakePractice(FakeNetwork()))
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `download offline is refused with a message and nothing is fetched`() = runTest {
        val content = FakeContent()
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork(false)), FakeNetwork(online = false))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.download(CefrLevel.A1)

        assertEquals(R.string.practice_download_needs_internet, vm.uiState.value.message?.text)
        assertEquals(emptyList<Any>(), content.packs.value)
    }

    @Test
    fun `download shows progress, can complete, and reports failure without losing data`() = runTest {
        val content = FakeContent()
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val gate = CompletableDeferred<Unit>().also { content.downloadGate = it }
        vm.download(CefrLevel.A1)
        assertEquals(1, vm.uiState.value.levels.first { it.level == CefrLevel.A1 }.downloading)
        gate.complete(Unit)
        val row = vm.uiState.value.levels.first { it.level == CefrLevel.A1 }
        assertNull(row.downloading)
        assertEquals(3, row.pack?.questionCount)
        assertEquals(UiMessage(R.plurals.practice_download_done, 3, plural = true), vm.uiState.value.message)

        content.downloadGate = null
        content.downloadResult = networkFailure
        vm.download(CefrLevel.B1)
        assertEquals(R.string.practice_download_failed_network, vm.uiState.value.message?.text)
        assertNull(vm.uiState.value.levels.first { it.level == CefrLevel.B1 }.pack)
    }

    @Test
    fun `cancelling a download leaves the level not downloaded`() = runTest {
        val content = FakeContent().apply { downloadGate = CompletableDeferred() }
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.download(CefrLevel.C1)
        vm.cancelDownload(CefrLevel.C1)

        val row = vm.uiState.value.levels.first { it.level == CefrLevel.C1 }
        assertNull(row.downloading)
        assertNull(row.pack)
    }

    @Test
    fun `manual sync reports the outcome`() = runTest {
        val practice = FakePractice(FakeNetwork()).apply { syncReport = SyncReport(0, 2, 0, SyncReport.StopReason.SESSION_EXPIRED) }
        val vm = HomeViewModel("u1", FakeContent(), practice, FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.syncNow()

        assertEquals(R.string.practice_sync_sign_in, vm.uiState.value.message?.text)
        assertEquals(false, vm.uiState.first().syncing)
    }

    @Test
    fun `staff menu follows the roles reported by the server`() = runTest {
        val staff = object : xyz.kurdcloud.englishmaster.core.data.StaffRepository {
            override suspend fun myAccess() = xyz.kurdcloud.englishmaster.core.network.ApiResult.Success(
                xyz.kurdcloud.englishmaster.core.model.StaffAccess(canReviewQuestions = true, canManageUsers = false))
            override suspend fun users(search: String?) = TODO()
            override suspend fun setRole(userId: String, role: xyz.kurdcloud.englishmaster.core.model.Role, granted: Boolean) = TODO()
            override suspend fun setSuspended(userId: String, suspended: Boolean) = TODO()
            override suspend fun questions(level: CefrLevel) = TODO()
            override suspend fun review(questionId: String, approved: Boolean, status: String?) = TODO()
            override suspend fun recentActivity() = TODO()
        }
        val vm = HomeViewModel("u1", FakeContent(), FakePractice(FakeNetwork()), FakeNetwork(), staff)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.staffAccess.canReviewQuestions)

        val student = HomeViewModel("u1", FakeContent(), FakePractice(FakeNetwork()), FakeNetwork())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { student.uiState.collect {} }
        assertEquals(false, student.uiState.value.staffAccess.any)
    }
}
