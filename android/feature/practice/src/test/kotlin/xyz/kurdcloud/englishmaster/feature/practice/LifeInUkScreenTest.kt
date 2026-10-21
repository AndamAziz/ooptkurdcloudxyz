package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LifeInUkChapter

@RunWith(RobolectricTestRunner::class)
class LifeInUkScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun beforeDownloadOnlyTheDownloadButtonAndOfficialLinksShow() {
        val content = FakeContent()
        val vm = LifeInUkViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork())
        compose.setContent { EnglishMasterTheme { LifeInUkScreen(vm, LifeInUkActions()) } }

        compose.onNodeWithText("Pass the official Life in the UK test").assertIsDisplayed()
        compose.onNodeWithTag("lituk_mock_start").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("lituk_link_book"))
        compose.onNodeWithTag("lituk_link_book").assertIsDisplayed()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("lituk_download"))
        compose.onNodeWithTag("lituk_download").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("lituk_pack_count"))
        compose.onNodeWithText("3 questions ready").assertIsDisplayed()
    }

    @Test
    fun chaptersAndMockTestOpenFromTheCoursePage() {
        val content = FakeContent().apply {
            packs.value = listOf(LevelPack(CefrLevel.LIFE_IN_UK, 400, 0))
            taggedQuestions = mapOf(LifeInUkChapter.HISTORY.tag to listOf(question("h1")))
        }
        val vm = LifeInUkViewModel("u1", content, FakePractice(FakeNetwork()), FakeNetwork())
        var chapter: LifeInUkChapter? = null
        var mock = false
        compose.setContent {
            EnglishMasterTheme { LifeInUkScreen(vm, LifeInUkActions(onPractiseChapter = { chapter = it }, onMockTest = { mock = true })) }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag("lituk_mock_start"))
        compose.onNodeWithTag("lituk_mock_start").performClick()
        assertEquals(true, mock)

        list.performScrollToNode(hasTestTag("lituk_chapter_1"))
        compose.onNodeWithTag("lituk_chapter_1").performClick() // no questions yet: not clickable
        assertEquals(null, chapter)
        list.performScrollToNode(hasTestTag("lituk_chapter_3"))
        compose.onNodeWithTag("lituk_chapter_3").performClick()
        assertEquals(LifeInUkChapter.HISTORY, chapter)
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun aMockTestCanBeTakenAndShowsTheResultInSorani() {
        val questions = (1..24).map { question("m$it") }
        val vm = MockTestViewModel("u1", FakeContent(questions), FakePractice(FakeNetwork()))
        compose.setContent { EnglishMasterTheme { MockTestScreen(vm, onClose = {}, onPractiseMistakes = {}) } }

        compose.onNodeWithTag("mock_timer").assertIsDisplayed()
        compose.onNodeWithTag("mock_counter").assertExists()
        repeat(23) {
            compose.onNodeWithTag("mock_option_B").performScrollTo().performClick()
            compose.onNodeWithTag("mock_next").performScrollTo().performClick()
        }
        compose.onNodeWithTag("mock_option_A").performScrollTo().performClick() // last one wrong
        compose.onNodeWithTag("mock_finish").performScrollTo().performClick()
        compose.onNodeWithTag("mock_confirm_finish").performClick()

        compose.onNodeWithTag("mock_result_title").assertIsDisplayed()
        compose.onNodeWithText("دەرچوویت! 🎉").assertIsDisplayed()
        compose.onNodeWithText("23/24").assertIsDisplayed()
        compose.onNodeWithTag("mock_review_item").performScrollTo().assertIsDisplayed()
    }
}
