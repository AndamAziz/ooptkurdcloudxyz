package xyz.kurdcloud.englishmaster

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity and dependency graph (no server needed). */
@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "en")
    fun firstLaunchShowsWelcomeAndReachesSignIn() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Create account").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create account").assertIsDisplayed()
        compose.onNodeWithText("I already have an account").performScrollTo().performClick()
        compose.onNodeWithText("Forgot password?").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun unconfiguredBuildExplainsItselfInsteadOfFailingSilently() {
        assumeTrue("only meaningful for builds without Supabase configuration", BuildConfig.SUPABASE_URL.isEmpty())
        compose.onNodeWithText("I already have an account").performScrollTo().performClick()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("learner@example.com")
        fields[1].performTextInput("abcdef12")
        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithTag("auth_message").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun soraniWelcomeScreen() {
        compose.onNodeWithText("دروستکردنی هەژمار").assertIsDisplayed()
    }
}
