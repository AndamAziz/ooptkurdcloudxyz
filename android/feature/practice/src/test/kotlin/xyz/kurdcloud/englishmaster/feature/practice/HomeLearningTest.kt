package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.model.Topic
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
    var checkpoints: List<xyz.kurdcloud.englishmaster.core.model.CheckpointStatus> = emptyList(),
    var placementStatus: xyz.kurdcloud.englishmaster.core.model.PlacementStatus? = null,
) : LearningRepository {
    override suspend fun checkpoints() = ApiResult.Success(checkpoints)
    override suspend fun placementStatus() = placementStatus?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.Network)
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
                HomeScreen(vm, {}, actions = HomeActions(onStartPractice = {}, onTakeExam = { exam = it }))
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
    fun checkpointsShowWhatIsPassedOpenAndLocked() {
        fun cp(n: Int, unlocked: Boolean, passed: Boolean, required: Int) =
            xyz.kurdcloud.englishmaster.core.model.CheckpointStatus(CefrLevel.A1, n, required, unlocked, passed, if (passed) 14 else null, if (passed) 15 else null, null, false)
        val learning = FakeLearningRepo(checkpoints = listOf(cp(1, true, true, 10), cp(2, true, false, 20), cp(3, false, false, 28)))
        var started: Pair<CefrLevel, Int>? = null
        compose.setContent {
            EnglishMasterTheme {
                HomeScreen(vm(learning), {}, actions = HomeActions(onStartPractice = {}, onCheckpoint = { l, n -> started = l to n }))
            }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag("checkpoint_A1_2"))
        compose.onNodeWithText("Checkpoint 1 passed (14/15)").assertIsDisplayed()
        compose.onNodeWithText("Checkpoint 2 is ready").assertIsDisplayed()
        list.performScrollToNode(hasText("Checkpoint 3 opens after 28 questions practised"))
        compose.onNodeWithText("Checkpoint 3 opens after 28 questions practised").assertIsDisplayed()
        compose.onNodeWithTag("checkpoint_A1_3").assertDoesNotExist()
        compose.onNodeWithTag("checkpoint_A1_2").performClick()
        assertEquals(CefrLevel.A1 to 2, started)
    }

    @Test
    fun aPlacementResultWaitingForConfirmationIsOffered() {
        val learning = FakeLearningRepo(
            placement = CefrLevel.B1,
            placementStatus = xyz.kurdcloud.englishmaster.core.model.PlacementStatus(2, null, false, confirmLevel = CefrLevel.A2),
        )
        var confirm = false
        compose.setContent {
            EnglishMasterTheme { HomeScreen(vm(learning), {}, actions = HomeActions(onStartPractice = {}, onConfirmPlacement = { confirm = true })) }
        }
        compose.onNodeWithText("Confirm your level: A2").assertIsDisplayed()
        compose.onNodeWithTag("home_confirm_placement").performClick()
        assertEquals(true, confirm)
    }

    @Test
    fun howToEarnACertificateIsExplained() {
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(), {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("how_certificates"))
        compose.onNodeWithTag("how_certificates").performClick()
        compose.onNodeWithText("Score 80% or more, then your teacher approves the result and your certificate is issued as a PDF with a QR code anyone can scan to check it.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun recommendedLevelInSorani() {
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(FakeLearningRepo(placement = CefrLevel.B1)), {}) } }
        compose.onNodeWithText("ئاستی پێشنیارکراوت: B1").assertIsDisplayed()
    }

    @Test
    fun topicsAppearOnceALevelIsDownloadedAndOpenATopicSession() {
        val content = FakeContent().apply {
            topicQuestions = mapOf(Topic.BRITAIN to listOf(question("k1"), question("k2")))
            packs.value = listOf(LevelPack(CefrLevel.A1, 3, 0))
        }
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork(), learning = FakeLearningRepo(), clock = { 0L })
        var opened: Topic? = null
        var openedLifeInUk = false
        compose.setContent {
            EnglishMasterTheme {
                HomeScreen(vm, {}, actions = HomeActions(onStartPractice = {}, onPractiseTopic = { opened = it }, onLifeInUk = { openedLifeInUk = true }))
            }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag("topic_britain"))
        compose.onNodeWithText("2 questions").assertIsDisplayed()
        compose.onNodeWithTag("topic_britain").performClick()
        assertEquals(Topic.BRITAIN, opened)

        // A topic without questions cannot be opened.
        list.performScrollToNode(hasTestTag("topic_stories"))
        compose.onNodeWithTag("topic_stories").performClick()
        assertEquals(Topic.BRITAIN, opened)

        // Life in the UK has its own course card instead of a topic tile.
        compose.onNodeWithTag("topic_life_in_uk").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("home_lituk"))
        compose.onNodeWithTag("home_lituk").performClick()
        assertEquals(true, openedLifeInUk)
    }

    @Test
    fun learnersHaveNoPerLevelUpdateOrRemove() {
        val content = FakeContent().apply { packs.value = listOf(LevelPack(CefrLevel.A1, 3, 0)) }
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork(), learning = FakeLearningRepo(), clock = { 0L })
        compose.setContent { EnglishMasterTheme { HomeScreen(vm, {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("level_A1"))
        // Updating is automatic, with one button in My account for every section.
        compose.onNodeWithTag("update_A1").assertDoesNotExist()
        compose.onNodeWithText("Update").assertDoesNotExist()
        compose.onNodeWithText("More").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        // Even if asked directly, the view model keeps a learner's questions.
        vm.remove(CefrLevel.A1)
        assertEquals(1, content.packs.value.size)
    }

    @Test
    fun staffKeepTheRemoveOption() {
        val content = FakeContent().apply { packs.value = listOf(LevelPack(CefrLevel.A1, 3, 0)) }
        val staff = object : xyz.kurdcloud.englishmaster.core.data.StaffRepository {
            override suspend fun myAccess() = ApiResult.Success(xyz.kurdcloud.englishmaster.core.model.StaffAccess(true, false))
            override suspend fun users(search: String?) = ApiResult.Failure(ApiError.Forbidden)
            override suspend fun setRole(userId: String, role: xyz.kurdcloud.englishmaster.core.model.Role, granted: Boolean) = ApiResult.Failure(ApiError.Forbidden)
            override suspend fun setSuspended(userId: String, suspended: Boolean) = ApiResult.Failure(ApiError.Forbidden)
            override suspend fun questions(level: CefrLevel) = ApiResult.Failure(ApiError.Forbidden)
            override suspend fun review(questionId: String, approved: Boolean, status: String?) = ApiResult.Failure(ApiError.Forbidden)
            override suspend fun recentActivity() = ApiResult.Failure(ApiError.Forbidden)
        }
        val vm = HomeViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork(), staff = staff, learning = FakeLearningRepo(), clock = { 0L })
        compose.setContent { EnglishMasterTheme { HomeScreen(vm, {}) } }
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag("level_A1"))
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun passedLevelShowsItIsWaitingForApproval() {
        val learning = FakeLearningRepo(
            levels = listOf(overview(CefrLevel.A2, 30, 28, unlocked = true).copy(lastExamScore = 18, lastExamTotal = 20, certificatePending = true)),
        )
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(learning), {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("level_status_A2"))
        compose.onNodeWithText("Passed with 18 / 20. Your teacher will review the result; the certificate appears here once it is approved.")
            .assertIsDisplayed()
        compose.onNodeWithTag("exam_A2").assertDoesNotExist()
    }

    @Test
    fun noTopicsBeforeAnyLevelIsDownloaded() {
        compose.setContent { EnglishMasterTheme { HomeScreen(vm(), {}) } }
        compose.onNodeWithTag("topics_section").assertDoesNotExist()
    }

    @Test
    fun offlineHomeFallsBackToLocalProgress() {
        val vm = vm(online = false)
        compose.setContent { EnglishMasterTheme { HomeScreen(vm, {}) } }
        compose.onNodeWithText("Welcome 👋").assertIsDisplayed()
        assertEquals(null, vm.uiState.value.levels.first().overview)
    }
}
