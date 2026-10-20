package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.Speaker
import xyz.kurdcloud.englishmaster.core.designsystem.speechText
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.QuestionMedia
import xyz.kurdcloud.englishmaster.core.model.Topic

@RunWith(RobolectricTestRunner::class)
class PracticeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "b+ckb")
    fun offlineAnswerShowsSavedStateInSoraniAndSessionCanFinish() {
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), FakePractice(FakeNetwork(online = false)))
        compose.setContent { EnglishMasterTheme { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }

        compose.onNodeWithText("She ___ a teacher (q1).").assertIsDisplayed()
        compose.onNodeWithTag("check_answer").assertIsNotEnabled()
        compose.onNodeWithTag("option_B").performScrollTo().performClick()
        compose.onNodeWithTag("check_answer").performScrollTo().performClick()

        compose.onNodeWithText("وەڵامەکە لەسەر ئەم ئامێرە پاشەکەوت کرا. کاتێک گەڕایتەوە سەر هێڵ هەڵدەسەنگێنرێت.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("next_question").performScrollTo().performClick()
        compose.onNodeWithTag("summary").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun onlineWrongAnswerRevealsCorrectOptionAndExplanation() {
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), FakePractice(FakeNetwork(online = true)))
        compose.setContent { EnglishMasterTheme { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }

        compose.onNodeWithTag("option_A").performScrollTo().performClick()
        compose.onNodeWithTag("check_answer").performScrollTo().performClick()

        compose.onNodeWithText("Not quite. The correct answer is:").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("B.  is").assertCountEquals(2) // the option itself and the feedback
        compose.onNodeWithText("Use is with she.").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "b+ckb")
    fun topicSessionShowsTopicTitleAndEachQuestionsLevel() {
        val content = FakeContent().apply { topicQuestions = mapOf(Topic.KURDISTAN to listOf(question("k1").copy(level = CefrLevel.B2))) }
        val vm = PracticeViewModel(null, "u1", content, FakePractice(FakeNetwork()), topic = Topic.KURDISTAN)
        compose.setContent { EnglishMasterTheme { PracticeScreen(null, vm, onExit = {}) } }

        compose.onNodeWithText("کوردستان").assertIsDisplayed()
        compose.onNodeWithTag("practice_counter").assertTextContains("B2", substring = true)
    }

    private class FakeSpeaker : Speaker {
        override var available by mutableStateOf(true)
        override var speakingId by mutableStateOf<String?>(null)
        val spoken = mutableListOf<String>()
        override fun speak(id: String, text: String) { speakingId = id; spoken += text }
        override fun stop() { speakingId = null }
    }

    @Test
    @Config(qualifiers = "en")
    fun listenButtonReadsTheQuestionAndCanBeStopped() {
        val speaker = FakeSpeaker()
        val q = question("q1").copy(passage = "Sam lives in London.")
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(q)), FakePractice(FakeNetwork()))
        compose.setContent {
            EnglishMasterTheme { CompositionLocalProvider(LocalSpeaker provides speaker) { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }
        }
        compose.onNodeWithTag("speak_question_q1").assertIsDisplayed().performClick()
        assertEquals("She blank a teacher (q1). A: am. B: is. C: are. D: be.", speaker.spoken.single())
        compose.onNodeWithContentDescription("Stop reading").performClick()
        assertEquals(null, speaker.speakingId)

        compose.onNodeWithTag("speak_passage_q1").performClick()
        assertEquals("Sam lives in London.", speaker.spoken.last())
    }

    @Test
    fun noListenButtonWithoutAnEnglishVoice() {
        val speaker = FakeSpeaker().apply { available = false }
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), FakePractice(FakeNetwork()))
        compose.setContent {
            EnglishMasterTheme { CompositionLocalProvider(LocalSpeaker provides speaker) { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }
        }
        compose.onNodeWithTag("question_prompt").assertIsDisplayed()
        compose.onNodeWithTag("speak_question_q1").assertDoesNotExist()
    }

    @Test
    fun speechTextReadsGapsAndLetters() {
        assertEquals("I have lived here blank 2019. A: since. B: for.", speechText("I have lived here ___ 2019.", listOf("since", "for")))
        assertEquals("Which river goes through London? A: the Thames. B: the Tigris.",
            speechText("Which river goes through London?", listOf("the Thames", "the Tigris")))
    }

    @Test
    fun readingPassageAndPictureAreShownAboveTheQuestion() {
        val q = question("r1").copy(
            passage = "Ali lives in Duhok with his family.",
            media = QuestionMedia(MediaType.IMAGE, "https://invalid.localhost/x.png", "A map", localPath = null),
        )
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(q)), FakePractice(FakeNetwork()))
        compose.setContent { EnglishMasterTheme { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }

        compose.onNodeWithText("Ali lives in Duhok with his family.").assertIsDisplayed()
        compose.onNodeWithTag("question_image").assertExists()
        compose.onNodeWithText("A map").assertExists()
    }
}
