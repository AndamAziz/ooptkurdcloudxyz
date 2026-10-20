package xyz.kurdcloud.englishmaster.feature.exam

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind

@RunWith(RobolectricTestRunner::class)
class ExamScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun learnerTakesTheExamAndSeesTheCertificateOffer() {
        val repo = FakeLearning(expiresAt = Long.MAX_VALUE / 2)
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo)
        var certificates = false
        compose.setContent { EnglishMasterTheme { ExamScreen(vm, onClose = {}, onViewCertificates = { certificates = true }, onPractise = {}) } }

        compose.onNodeWithText("Earn your A1 certificate").assertIsDisplayed()
        compose.onNodeWithText("Pass with 80% or more. Your teacher then approves the result and you receive a certificate with a QR verification code.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exam_name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exam_start").performScrollTo().performClick()

        compose.onNodeWithText("He ___ happy (q1).").assertIsDisplayed()
        compose.onNodeWithTag("exam_option_B").performScrollTo().performClick()
        compose.onNodeWithTag("exam_next").performScrollTo().performClick()
        compose.onNodeWithTag("exam_option_B").performScrollTo().performClick()
        compose.onNodeWithTag("exam_finish").performScrollTo().performClick()
        compose.onNodeWithTag("exam_confirm_submit").performClick()

        compose.onNodeWithText("Congratulations! You passed.").assertIsDisplayed()
        compose.onNodeWithTag("exam_view_certificate").performScrollTo().performClick()
        assert(certificates)
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun passedExamWaitsForTeacherApprovalInSorani() {
        val repo = FakeLearning(expiresAt = Long.MAX_VALUE / 2).apply { certificateApproved = false }
        val vm = ExamViewModel(ExamKind.LEVEL_FINAL, CefrLevel.A1, repo)
        compose.setContent { EnglishMasterTheme { ExamScreen(vm, onClose = {}, onViewCertificates = {}, onPractise = {}) } }

        compose.onNodeWithTag("exam_start").performScrollTo().performClick()
        compose.onNodeWithTag("exam_option_B").performScrollTo().performClick()
        compose.onNodeWithTag("exam_next").performScrollTo().performClick()
        compose.onNodeWithTag("exam_option_B").performScrollTo().performClick()
        compose.onNodeWithTag("exam_finish").performScrollTo().performClick()
        compose.onNodeWithTag("exam_confirm_submit").performClick()

        compose.onNodeWithTag("exam_awaiting_approval").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ئەنجامەکەت بۆ مامۆستاکەت نێردرا. هەر کە مامۆستا یان ئەدمین پەسەندی کرد بڕوانامەی A1 دەردەچێت — لە پەڕەی سەرەکی دەیبینیت.")
            .assertExists()
        compose.onNodeWithTag("exam_view_certificate").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun placementResultInSorani() {
        val vm = ExamViewModel(ExamKind.PLACEMENT, null, FakeLearning(expiresAt = Long.MAX_VALUE / 2))
        compose.setContent { EnglishMasterTheme { ExamScreen(vm, onClose = {}, onViewCertificates = {}, onPractise = {}) } }

        compose.onNodeWithText("ئاستی خۆت بدۆزەرەوە").assertIsDisplayed()
        compose.onNodeWithTag("exam_start").performScrollTo().performClick()
        compose.onNodeWithTag("exam_finish").performScrollTo().performClick()
        compose.onNodeWithTag("exam_confirm_submit").performClick()
        compose.onNodeWithText("ئاستی پێشنیارکراوت: B1").assertIsDisplayed()
    }
}
