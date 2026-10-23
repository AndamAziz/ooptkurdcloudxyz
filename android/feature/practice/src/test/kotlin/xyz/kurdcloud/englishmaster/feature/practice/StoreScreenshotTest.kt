package xyz.kurdcloud.englishmaster.feature.practice

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.Question
import java.io.File

/**
 * Google Play phone screenshots (1080x1920) of the real screens with sample data.
 * Run with STORE_SCREENSHOTS=/some/dir to save them; scripts/media/store_screenshots.sh frames them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(1080, bitmap.width)
        System.getenv("STORE_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun home(name: String) {
        val vm = HomeViewModel("u1", FakeContent(), FakePractice(FakeNetwork()), FakeNetwork(), learning = FakeLearningRepo(), clock = { 0L })
        compose.setContent { EnglishMasterTheme { HomeScreen(vm, {}) } }
        compose.waitForIdle()
        save(name)
    }

    private fun practice(name: String) {
        val q = Question("q1", CefrLevel.A1, "grammar", "My sister ___ a nurse at the city hospital.", listOf("am", "is", "are", "be"), 1, reviewed = true)
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(q)), FakePractice(FakeNetwork(online = true)))
        compose.setContent { EnglishMasterTheme { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }
        compose.onNodeWithTag("option_B").performScrollTo().performClick()
        compose.onNodeWithTag("check_answer").performScrollTo().performClick()
        compose.onNodeWithTag("feedback_title").performScrollTo()
        compose.waitForIdle()
        save(name)
    }

    @Test @Config(qualifiers = "en-w360dp-h640dp-xxhdpi")
    fun homeEnglish() = home("1-home-en")

    @Test @Config(qualifiers = "en-w360dp-h640dp-xxhdpi")
    fun practiceEnglish() = practice("2-practice-en")

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun homeSorani() = home("3-home-ckb")

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun practiceSorani() = practice("4-practice-ckb")
}
