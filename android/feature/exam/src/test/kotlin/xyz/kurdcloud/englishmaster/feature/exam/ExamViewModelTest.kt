package xyz.kurdcloud.englishmaster.feature.exam

import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.RGBLuminanceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.network.ApiError

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun passingExamShowsCertificateAndSendsLetters() = runTest(dispatcher) {
        val repo = FakeLearning()
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo, clock = { testScheduler.currentTime })
        runCurrent()
        assertEquals("Sara Ahmed", vm.uiState.value.displayName)

        vm.start(); runCurrent()
        assertEquals(ExamPhase.RUNNING, vm.uiState.value.phase)
        vm.select(1); vm.next(); vm.select(1)
        assertEquals(0, vm.uiState.value.unanswered)
        vm.submit(); runCurrent()

        val s = vm.uiState.value
        assertEquals(ExamPhase.FINISHED, s.phase)
        assertEquals(true, s.result?.passed)
        assertEquals("EM-ABCD-EFGH-JKLM", s.result?.certificateCode)
        assertEquals(mapOf("q1" to "B", "q2" to "B"), repo.submitted)
    }

    @Test
    fun failedSubmitKeepsAnswersForRetry() = runTest(dispatcher) {
        val repo = FakeLearning().apply { submitFailure = ApiError.Network }
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo, clock = { testScheduler.currentTime })
        vm.start(); runCurrent()
        vm.select(0)
        vm.submit(); runCurrent()

        assertEquals(ExamPhase.RUNNING, vm.uiState.value.phase)
        assertEquals(ExamProblem.OFFLINE, vm.uiState.value.problem)
        assertEquals(mapOf("q1" to 0), vm.uiState.value.answers)

        vm.submit(); runCurrent()
        assertEquals(false, vm.uiState.value.result?.passed)
        assertEquals(mapOf("q1" to "A"), repo.submitted)
    }

    @Test
    fun serverRefusalIsExplainedOnTheIntro() = runTest(dispatcher) {
        val repo = FakeLearning().apply { startFailure = ApiError.Validation("P0001", "retry_later") }
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo)
        vm.start(); runCurrent()
        assertEquals(ExamPhase.INTRO, vm.uiState.value.phase)
        assertEquals(ExamProblem.RETRY_LATER, vm.uiState.value.problem)
    }

    @Test
    fun answersAreSentAutomaticallyWhenTimeRunsOut() = runTest(dispatcher) {
        val repo = FakeLearning(expiresAt = 5_000)
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo, clock = { testScheduler.currentTime })
        vm.start(); runCurrent()
        vm.select(1)
        assertEquals(5L, vm.uiState.value.remainingSeconds)

        advanceTimeBy(5_001); runCurrent()

        assertEquals(ExamPhase.FINISHED, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.autoSubmitted)
        assertEquals(mapOf("q1" to "B"), repo.submitted)
    }

    @Test
    fun placementRecommendsALevelAndNameCanBeChanged() = runTest(dispatcher) {
        val repo = FakeLearning()
        val placement = ExamViewModel(ExamKind.PLACEMENT, null, repo, clock = { testScheduler.currentTime })
        runCurrent()
        assertNull(placement.uiState.value.displayName)
        placement.start(); runCurrent()
        placement.submit(); runCurrent()
        assertEquals(CefrLevel.B1, placement.uiState.value.placement?.recommendedLevel)

        val exam = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo)
        exam.updateName("  "); runCurrent()
        assertEquals(ExamProblem.INVALID_NAME, exam.uiState.value.problem)
        exam.updateName(" Sara A. Karim "); runCurrent()
        assertEquals("Sara A. Karim", exam.uiState.value.displayName)
    }

    @Test
    fun qrCodeDecodesToTheVerificationUrl() {
        val url = "https://example.supabase.co/functions/v1/verify-certificate?code=EM-ABCD-EFGH-JKLM"
        val m = qrMatrix(url, 200)
        val pixels = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else -1 }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(m.width, m.height, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }
}
