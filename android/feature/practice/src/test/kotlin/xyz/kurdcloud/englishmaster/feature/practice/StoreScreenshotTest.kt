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
import xyz.kurdcloud.englishmaster.core.model.KurdishText
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheoryQuestion
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import kotlinx.coroutines.runBlocking
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

    private fun practiceWithKurdish(name: String) {
        val q = Question(
            "q1", CefrLevel.A2, "vocabulary", "After the long journey, the children were very ___ and went straight to bed.",
            listOf("tired", "hungry", "excited", "angry"), 1, reviewed = true,
            kurdish = KurdishText(
                "دوای گەشتە درێژەکە، منداڵەکان زۆر ___ بوون و یەکسەر چوونە ناو جێگاکانیان.",
                listOf("ماندوو", "برسی", "بە پەرۆش", "تووڕە"), null,
            ),
        )
        val vm = PracticeViewModel(CefrLevel.A2, "u1", FakeContent(listOf(q)), FakePractice(FakeNetwork(online = true)))
        compose.setContent { EnglishMasterTheme { PracticeScreen(CefrLevel.A2, vm, onExit = {}) } }
        compose.onNodeWithTag("kurdish_toggle").performClick()
        compose.waitForIdle()
        save(name)
    }

    /** A chapter of the driving theory course, with real wording and its Sorani translation. */
    private fun drivingChapter() = listOf(
        TheoryQuestion(
            "t1", "alertness",
            "You're waiting to turn right at the end of a road. Your view is blocked by parked cars. What should you do?",
            "لە کۆتایی ڕێگایەکدا چاوەڕێی سوڕانەوەی ڕاستیت. ئۆتۆمبێلە ڕاگیراوەکان دیمەنت لێ دەشارنەوە. دەبێت چی بکەیت؟",
            "Edge forward slowly until you can see clearly",
            "بە هێواشی بەرەو پێش بچۆ تا بە ڕوونی دەبینیت",
            "Moving out slowly lets you see without putting yourself in the path of traffic.",
            "جوڵانی هێواش وا دەکات ببینیت بەبێ ئەوەی خۆت بخەیتە بەردەم هاتوچۆ.",
            null, 1,
        ),
        TheoryQuestion(
            "t2", "alertness", "What should you do before moving off?",
            "پێش بەڕێکەوتن دەبێت چی بکەیت؟",
            "Check the mirrors and look over your shoulder",
            "سەیری ئاوێنەکان بکە و لەسەر شانت تەماشا بکە",
            null, null, null, 1,
        ),
        TheoryQuestion(
            "t3", "alertness", "When may you use a hand-held mobile phone while driving?",
            "کەی دەتوانیت لە کاتی شۆفێریدا مۆبایل بە دەست بەکاربهێنیت؟",
            "Never, unless you're safely parked with the engine off",
            "هەرگیز، مەگەر بە سەلامەتی ڕاوەستابیت و بزوێنەر کوژاوەتەوە",
            null, null, null, 1,
        ),
        TheoryQuestion(
            "t4", "alertness", "What does a long white line along the centre of the road mean?",
            "هێڵی سپی درێژ لە ناوەڕاستی ڕێگادا چی دەگەیەنێت؟",
            "There's a hazard ahead — do not cross it unless it's safe",
            "مەترسییەک لە پێشەوەیە — مەیبڕە مەگەر سەلامەت بێت",
            null, null, null, 1,
        ),
    )

    private val drivingSections = listOf(
        TheorySection("alertness", "Alertness", "ئاگاداری", 26, 18, 15),
        TheorySection("attitude", "Attitude", "هەڵسوکەوت", 38, 12, 11),
        TheorySection("safety-and-vehicle", "Safety and your vehicle", "سەلامەتی و ئۆتۆمبێلەکەت", 82, 0, 0),
        TheorySection("safety-margins", "Safety margins", "بۆشایی سەلامەتی", 34, 0, 0),
        TheorySection("hazard-awareness", "Hazard awareness", "ئاگاداری لە مەترسی", 78, 0, 0),
    )

    // Every chapter has questions, so the course page shows the chapters rather than a download prompt.
    private fun theoryRepo() = FakeTheory(drivingSections.associate { it.slug to drivingChapter() }, drivingSections)

    private fun theoryCourse(name: String) {
        val repo = theoryRepo()
        runBlocking { drivingSections.forEach { repo.downloadSection(it.slug) } }
        val vm = TheoryViewModel(repo, FakeNetwork())
        compose.setContent { EnglishMasterTheme { TheoryScreen(vm, TheoryActions()) } }
        compose.waitForIdle()
        save(name)
    }

    private fun theoryQuestionCard(name: String, kurdish: Boolean) {
        val repo = theoryRepo()
        runBlocking { repo.downloadSection("alertness") }
        val vm = TheorySessionViewModel("alertness", TheoryMode.TEST, repo)
        compose.setContent { EnglishMasterTheme { TheorySessionScreen(vm, "Alertness", onExit = {}) } }
        if (kurdish) compose.onNodeWithTag("kurdish_toggle").performClick()
        compose.waitForIdle()
        save(name)
    }

    @Test @Config(qualifiers = "en-w360dp-h640dp-xxhdpi")
    fun drivingTheoryCourseEnglish() = theoryCourse("6-driving-theory-en")

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun drivingTheoryQuestionSorani() = theoryQuestionCard("7-driving-theory-ckb", kurdish = true)

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun practiceKurdishTranslation() = practiceWithKurdish("5-practice-kurdish-translation")

    @Test @Config(qualifiers = "en-w360dp-h640dp-xxhdpi")
    fun homeEnglish() = home("1-home-en")

    @Test @Config(qualifiers = "en-w360dp-h640dp-xxhdpi")
    fun practiceEnglish() = practice("2-practice-en")

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun homeSorani() = home("3-home-ckb")

    @Test @Config(qualifiers = "b+ckb-w360dp-h640dp-xxhdpi")
    fun practiceSorani() = practice("4-practice-ckb")
}
