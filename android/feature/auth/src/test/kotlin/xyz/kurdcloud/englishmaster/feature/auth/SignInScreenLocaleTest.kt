package xyz.kurdcloud.englishmaster.feature.auth

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme

@RunWith(RobolectricTestRunner::class)
class SignInScreenLocaleTest {
    @get:Rule val compose = createComposeRule()

    private fun render(): () -> LayoutDirection {
        var direction = LayoutDirection.Ltr
        val viewModel = SignInViewModel(FakeAuthRepository())
        compose.setContent {
            direction = LocalLayoutDirection.current
            EnglishMasterTheme {
                SignInScreen(viewModel, onSignedIn = {}, onForgotPassword = {}, onCreateAccount = {}, onBack = {})
            }
        }
        return { direction }
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun soraniLocaleRendersKurdishRightToLeft() {
        val direction = render()
        compose.onNodeWithText("وشەی نهێنیت لەبیرچووە؟").assertIsDisplayed()
        assertEquals(LayoutDirection.Rtl, direction())

        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithText("ئیمەیڵێکی دروست بنووسە").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun englishLocaleRendersLeftToRight() {
        val direction = render()
        compose.onNodeWithText("Forgot password?").assertIsDisplayed()
        assertEquals(LayoutDirection.Ltr, direction())
    }
}
