package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollToNodeAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamResult
import xyz.kurdcloud.englishmaster.core.model.ExamSession
import xyz.kurdcloud.englishmaster.core.model.LearnerProfile
import xyz.kurdcloud.englishmaster.core.model.LevelOverview
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

private fun overview(level: CefrLevel, answered: Int, correct: Int, unlocked: Boolean = false, certificate: String? = null) =
    LevelOverview(level, 40, answered, correct, 30, 70, unlocked, null, false, if (certificate != null) 19 else null,
        if (certificate != null) 20 else null, certificate, null)

class FakeLearningRepo(
    var levels: List<LevelOverview> = listOf(
        overview(CefrLevel.A1, 22, 14),
        overview(CefrLevel.A2, 30, 25, unlocked = true),
        overview(CefrLevel.B1, 35, 30, unlocked = true, certificate = "EM-ABCD-EFGH-JKLM"),
    ),
    var placement: CefrLevel? = null,
) : LearningRepository {
    override suspend fun overview() = ApiResult.Success(levels)
    override suspend fun profile() = ApiResult.Success(LearnerProfile("Sara", placement, 87, 3))
    override suspend fun updateDisplayName(name: String) = ApiResult.Success(name)
    override suspend fun startLevelExam(level: CefrLevel): ApiResult<ExamSession> = ApiResult.Failure(ApiError.Network)
    override suspend fun submitLevelExam(attemptId: String, answers: Map<String, String>): ApiResult<ExamResult> = ApiResult.Failure(ApiError.Network)
    override suspend fun startPlacement(): ApiResult<ExamSession> = ApiResult.Failure(ApiError.Network)
    override suspend fun submitPlacement(attemptId: String, answers: Map<String, String>): ApiResult<PlacementResult> = ApiResult.Failure(ApiError.Network)
    override suspend fun certificates(): ApiResult<List<Certificate>> = ApiResult.Success(emptyList())
}

@RunWith(RobolectricTestRunner::class)
class HomeLearningTest {
    @get:Rule val compose = createComposeRule()

    private fun vm(learning: FakeLearningRepo = FakeLearningRepo(), online: Boolean = true) =
        HomeViewModel("u1", FakeContent(), FakePractice(FakeNetwork(online)), FakeNetwork(online), learning = learning, clock = { 0L })

    @Test
    fun dashboardExplainsTheWayToEachCertificate() {
        val vm = vm()
        var exam: CefrLevel? = null
        compose.setContent {
            EnglishMasterTheme {
                HomeScreen(vm, "کوردی", {}, {}, {}, actions = HomeActions(onStartPractice = {}, onTakeExam = { exam = it }))
            }
        }
        compose.onNodeWithText("Hello, Sara 👋").assertIsDisplayed()
        compose.onNodeWithText("87").assertIsDisplayed()
        compose.onNodeWithTag("home_placement").assertIsDisplayed()

        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag("level_status_A1"))
        compose.onNodeWithText("Practise 8 more questions with 70% correct to unlock the final exam.").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("exam_A2"))
        compose.onNodeWithTag("exam_A2").performClick()
        assertEquals(CefrLevel.A2, exam)
        list.performScrollToNode(hasTestTag("level_status_B1"))
        compose.onNodeWithText("Certificate earned with 19 / 20.").assertIsDisplayed()
    }

    @Test
    fun howToEarnACertificateIsExplained() {
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(), "کوردی", {}, {}, {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("how_certificates"))
        compose.onNodeWithTag("how_certificates").performClick()
        compose.onNodeWithText("Score 80% or more and your certificate is issued at once, as a PDF with a QR code anyone can scan to check it.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun recommendedLevelInSorani() {
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(FakeLearningRepo(placement = CefrLevel.B1)), "English", {}, {}, {}) } }
        compose.onNodeWithText("ئاستی پێشنیارکراوت: B1").assertIsDisplayed()
    }

    @Test
    fun offlineHomeFallsBackToLocalProgress() {
        val vm = vm(online = false)
        compose.setContent { EnglishMasterTheme { HomeScreen(vm, "کوردی", {}, {}, {}) } }
        compose.onNodeWithText("Welcome 👋").assertIsDisplayed()
        assertEquals(null, vm.uiState.value.levels.first().overview)
    }
}
