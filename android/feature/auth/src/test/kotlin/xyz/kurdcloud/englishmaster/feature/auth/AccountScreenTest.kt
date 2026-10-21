package xyz.kurdcloud.englishmaster.feature.auth

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.data.AccountRepository
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.model.ExamResult
import xyz.kurdcloud.englishmaster.core.model.ExamSession
import xyz.kurdcloud.englishmaster.core.model.LearnerProfile
import xyz.kurdcloud.englishmaster.core.model.LevelOverview
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

internal class AccountScreenTestLearning : LearningRepository {
    var name = "Andam Aziz"
    override suspend fun overview(): ApiResult<List<LevelOverview>> = ApiResult.Success(emptyList())
    override suspend fun profile() = ApiResult.Success(LearnerProfile(name, null, 0, 0))
    override suspend fun updateDisplayName(name: String): ApiResult<String> { this.name = name; return ApiResult.Success(name) }
    override suspend fun startLevelExam(level: CefrLevel): ApiResult<ExamSession> = ApiResult.Failure(ApiError.Network)
    override suspend fun submitLevelExam(attemptId: String, answers: Map<String, String>): ApiResult<ExamResult> = ApiResult.Failure(ApiError.Network)
    override suspend fun startPlacement(): ApiResult<ExamSession> = ApiResult.Failure(ApiError.Network)
    override suspend fun submitPlacement(attemptId: String, answers: Map<String, String>): ApiResult<PlacementResult> = ApiResult.Failure(ApiError.Network)
    override suspend fun certificates(): ApiResult<List<Certificate>> = ApiResult.Success(emptyList())
}

private class FakeAccount(var result: ApiResult<Unit> = ApiResult.Success(Unit)) : AccountRepository {
    var calls = 0
    override suspend fun deleteAccount(): ApiResult<Unit> { calls++; return result }
}

private fun staff(access: StaffAccess) = object : StaffRepository {
    override suspend fun myAccess() = ApiResult.Success(access)
    override suspend fun users(search: String?) = ApiResult.Failure(ApiError.Forbidden)
    override suspend fun setRole(userId: String, role: Role, granted: Boolean) = ApiResult.Failure(ApiError.Forbidden)
    override suspend fun setSuspended(userId: String, suspended: Boolean) = ApiResult.Failure(ApiError.Forbidden)
    override suspend fun questions(level: CefrLevel) = ApiResult.Failure(ApiError.Forbidden)
    override suspend fun review(questionId: String, approved: Boolean, status: String?) = ApiResult.Failure(ApiError.Forbidden)
    override suspend fun recentActivity() = ApiResult.Failure(ApiError.Forbidden)
}

@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {
    @get:Rule val compose = createComposeRule()
    private val auth = FakeAuthRepository().apply { session.value = AuthSession("u1", "sara@example.com", "a", "r", Long.MAX_VALUE / 2) }

    private fun render(vm: AccountViewModel, onSignedOut: () -> Unit = {}, onPrivacy: () -> Unit = {}) = compose.setContent {
        EnglishMasterTheme {
            AccountScreen(vm, "0.9.0", pendingAnswers = 0, actions = AccountActions("English", {}, onPrivacy), onSignedOut = onSignedOut, onBack = {})
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun learnerSeesProfileRenamesAndDeletesTheirAccount() {
        val learning = AccountScreenTestLearning()
        val account = FakeAccount()
        var signedOut = false
        var privacy = false
        render(AccountViewModel(auth, learning, account, staff(StaffAccess.NONE)), onSignedOut = { signedOut = true }, onPrivacy = { privacy = true })

        compose.onNodeWithTag("account_name").assertIsDisplayed()
        compose.onAllNodesWithText("sara@example.com").assertCountEquals(2) // header and profile row
        compose.onNodeWithTag("account_role").assertIsDisplayed()
        compose.onNodeWithText("Learner").assertIsDisplayed()

        compose.onNodeWithTag("account_privacy").performScrollTo().performClick()
        assertTrue(privacy)

        compose.onNodeWithTag("account_edit_name").performScrollTo().performClick()
        compose.onNodeWithTag("account_name_field").performTextReplacement("Sara Karim")
        compose.onNodeWithTag("account_name_save").performClick()
        assertEquals("Sara Karim", learning.name)
        compose.onNodeWithText("Name saved.").assertExists()
        compose.mainClock.advanceTimeBy(10_000) // let the snackbar go away

        compose.onNodeWithTag("account_delete").performScrollTo().performClick()
        compose.onNodeWithTag("account_delete_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("account_delete_understood").performClick()
        compose.onNodeWithTag("account_delete_confirm").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, account.calls)
        assertTrue(signedOut)
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun teachersCannotDeleteTheirAccountInTheApp() {
        render(AccountViewModel(auth, AccountScreenTestLearning(), FakeAccount(), staff(StaffAccess(canReviewQuestions = true, canManageUsers = false))))
        compose.onNodeWithText("مامۆستا").assertIsDisplayed()
        compose.onNodeWithTag("account_delete").assertDoesNotExist()
        compose.onNodeWithText("هەژماری مامۆستا و ئەدمین لەلایەن ئەدمینەوە لادەبرێت. سەرەتا داوا لە ئەدمین بکە ڕۆڵەکەت لابەرێت.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun failedDeletionKeepsTheUserSignedIn() {
        val account = FakeAccount(ApiResult.Failure(ApiError.Network))
        var signedOut = false
        val vm = AccountViewModel(auth, AccountScreenTestLearning(), account, staff(StaffAccess.NONE))
        render(vm, onSignedOut = { signedOut = true })
        vm.deleteAccount()
        compose.waitForIdle()
        assertEquals(false, signedOut)
        assertEquals(false, vm.uiState.value.deleted)
    }

    @Test
    @Config(qualifiers = "en")
    fun signOutAsksFirst() {
        var signedOut = false
        render(AccountViewModel(auth, AccountScreenTestLearning(), FakeAccount(), staff(StaffAccess.NONE)), onSignedOut = { signedOut = true })
        compose.onNodeWithTag("account_sign_out").performScrollTo().performClick()
        compose.onNodeWithTag("account_sign_out_confirm").performClick()
        compose.waitForIdle()
        assertTrue(signedOut)
        assertTrue("signOut" in auth.calls)
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun privacyPolicyIsReadableInSorani() {
        compose.setContent { EnglishMasterTheme { PrivacyScreen(onBack = {}) } }
        compose.onNodeWithText("چی کۆدەکەینەوە").assertIsDisplayed()
    }
}
