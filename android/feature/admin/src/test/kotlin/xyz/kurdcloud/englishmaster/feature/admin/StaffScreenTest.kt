package xyz.kurdcloud.englishmaster.feature.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.StaffAccess

@RunWith(RobolectricTestRunner::class)
class StaffScreenTest {
    @get:Rule val compose = createComposeRule()
    private val repo = FakeStaffRepository()

    private fun render(access: StaffAccess) {
        val users = UsersViewModel(repo)
        val questions = QuestionsViewModel(repo)
        val activity = ActivityViewModel(repo)
        compose.setContent {
            EnglishMasterTheme {
                StaffScreen(access, currentUserId = "me", usersViewModel = { users }, questionsViewModel = { questions },
                    activityViewModel = { activity }, onBack = {})
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun teacherCanCorrectNamesAndReviewQuestionsButNotChangeRoles() {
        render(StaffAccess(canReviewQuestions = true, canManageUsers = false))
        compose.onNodeWithText("Activity").assertDoesNotExist()
        val sara = hasAnyAncestor(hasTestTag("user_sara@example.com"))
        compose.onNodeWithTag("users_list").performScrollToNode(hasTestTag("user_sara@example.com"))
        compose.onNode(hasText("Make teacher") and sara).assertDoesNotExist()
        compose.onNodeWithTag("rename_sara@example.com").performClick()
        compose.onNodeWithTag("rename_field").performTextReplacement("Sara Ahmed Karim")
        compose.onNodeWithTag("rename_save").performClick()
        compose.onNodeWithText("Sara Ahmed Karim").assertIsDisplayed()

        compose.onNodeWithText("Questions").performClick()
        compose.onNodeWithText("I ___ a student.").assertIsDisplayed()
        compose.onNodeWithTag("approve_q1").performScrollTo().performClick()
        compose.onNodeWithText("Approved").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun adminCannotDemoteOrSuspendThemselvesFromTheUi() {
        render(StaffAccess(canReviewQuestions = true, canManageUsers = true))
        val mine = hasAnyAncestor(hasTestTag("user_andam@example.com"))
        compose.onNode(hasText("Remove admin") and mine).assertIsNotEnabled()
        compose.onNode(hasText("Suspend") and mine).assertIsNotEnabled()
        compose.onNodeWithTag("users_list").performScrollToNode(hasTestTag("user_sara@example.com"))
        val sara = hasAnyAncestor(hasTestTag("user_sara@example.com"))
        compose.onNode(hasText("Make teacher") and sara).assertIsEnabled().performClick()
        compose.onNode(hasText("Remove teacher") and sara).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun soraniTabs() {
        render(StaffAccess(canReviewQuestions = true, canManageUsers = true))
        compose.onNodeWithText("بەکارهێنەران").assertIsDisplayed()
        compose.onNodeWithText("پرسیارەکان").performClick()
        compose.onNodeWithText("I ___ a student.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun teacherApprovesALearnersCertificateButNotTheirOwn() {
        val certs = CertificateRequestsViewModel(repo)
        val users = UsersViewModel(repo)
        val questions = QuestionsViewModel(repo)
        val activity = ActivityViewModel(repo)
        compose.setContent {
            EnglishMasterTheme {
                StaffScreen(StaffAccess(canReviewQuestions = true, canManageUsers = false), currentUserId = "me",
                    usersViewModel = { users }, questionsViewModel = { questions },
                    activityViewModel = { activity }, onBack = {}, certificatesViewModel = { certs })
            }
        }
        // The approval queue is the first tab.
        compose.onNodeWithTag("cert_request_c1").assertIsDisplayed()
        compose.onNodeWithTag("cert_approve_c2").assertDoesNotExist()
        compose.onNodeWithText("This is your own exam: another teacher or an admin must review it.").assertExists()

        compose.onNodeWithTag("cert_approve_c1").performClick()
        compose.onNodeWithText("Approve the A2 certificate?").assertIsDisplayed()
        compose.onNodeWithTag("cert_confirm_approve").performClick()
        compose.onNodeWithTag("cert_request_c1").assertDoesNotExist()
        assertTrue("certReview:c1:true:null" in repo.calls)
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun adminSeesFourScrollableTabsInSorani() {
        val certs = CertificateRequestsViewModel(repo)
        val users = UsersViewModel(repo)
        val questions = QuestionsViewModel(repo)
        val activity = ActivityViewModel(repo)
        compose.setContent {
            EnglishMasterTheme {
                StaffScreen(StaffAccess(canReviewQuestions = true, canManageUsers = true), currentUserId = "me",
                    usersViewModel = { users }, questionsViewModel = { questions },
                    activityViewModel = { activity }, onBack = {}, certificatesViewModel = { certs })
            }
        }
        compose.onNodeWithText("بڕوانامەکان").assertIsDisplayed()
        compose.onNodeWithTag("cert_reject_c1").performClick()
        compose.onNodeWithTag("cert_reject_note").performTextReplacement("Retake with a teacher")
        compose.onNodeWithTag("cert_confirm_reject").performClick()
        assertTrue("certReview:c1:false:Retake with a teacher" in repo.calls)
    }
}
