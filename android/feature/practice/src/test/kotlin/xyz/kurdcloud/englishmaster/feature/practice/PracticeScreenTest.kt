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
import xyz.kurdcloud.englishmaster.core.designsystem.questionSpeech
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

        compose.onNodeWithTag("feedback_title").performScrollTo().assertTextContains("Not quite")
        compose.onNodeWithText("A.  am").performScrollTo().assertIsDisplayed() // your answer
        compose.onNodeWithText("B.  is").performScrollTo().assertIsDisplayed() // the correct answer
        compose.onNodeWithText("You chose “am”, but according to the official handbook the right answer is “is”:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("feedback_explanation").performScrollTo().assertTextContains("Use is with she.")
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
        override var autoRead by mutableStateOf(false)
        val spoken = mutableListOf<String>()
        override fun speak(id: String, text: String) { speakingId = id; spoken += text }
        override fun stop() { speakingId = null }
        override fun updateAutoRead(on: Boolean) { autoRead = on }
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
        compose.onNodeWithTag("listen_pill").assertIsDisplayed().performClick()
        assertEquals("She … a teacher (q1). A: am. B: is. C: are. D: be.", speaker.spoken.single())
        compose.onNodeWithText("Stop").performClick()
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
        compose.onNodeWithTag("listen_controls").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun autoReadReadsTheQuestionAndThenTheExplanation() {
        val speaker = FakeSpeaker().apply { autoRead = true }
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), FakePractice(FakeNetwork(online = true)))
        compose.setContent {
            EnglishMasterTheme { CompositionLocalProvider(LocalSpeaker provides speaker) { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }
        }
        compose.waitForIdle()
        assertEquals("She … a teacher (q1). A: am. B: is. C: are. D: be.", speaker.spoken.single())
        compose.onNodeWithTag("option_C").performScrollTo().performClick()
        compose.onNodeWithTag("check_answer").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Not quite. You chose C: are. The correct answer is B: is. Why? Use is with she.", speaker.spoken.last())
        // Turning auto-read off from the listening bar.
        compose.onNodeWithTag("listen_auto").performScrollTo().performClick()
        assertEquals(false, speaker.autoRead)
    }

    @Test
    @Config(qualifiers = "en")
    fun theExplanationCanBeHeardAgain() {
        val speaker = FakeSpeaker()
        val vm = PracticeViewModel(CefrLevel.A1, "u1", FakeContent(listOf(question("q1"))), FakePractice(FakeNetwork(online = true)))
        compose.setContent {
            EnglishMasterTheme { CompositionLocalProvider(LocalSpeaker provides speaker) { PracticeScreen(CefrLevel.A1, vm, onExit = {}) } }
        }
        compose.onNodeWithTag("option_B").performScrollTo().performClick()
        compose.onNodeWithTag("check_answer").performScrollTo().performClick()
        compose.onNodeWithTag("feedback_title").performScrollTo().assertTextContains("Correct! Well done")
        compose.onNodeWithTag("speak_feedback_q1").performScrollTo().performClick()
        assertEquals("Correct! The answer is B: is. Why? Use is with she.", speaker.spoken.last())
    }

    @Test
    fun explanationsSplitTheHandbookReference() {
        val parts = explanationParts("Handbook ch. 3 (A long and illustrious history): Alfred defeated the Vikings.")
        assertEquals(3, parts.chapter)
        assertEquals("A long and illustrious history", parts.chapterTitle)
        assertEquals("Alfred defeated the Vikings.", parts.body)
        assertEquals(null, explanationParts("Use is with she.").chapter)
    }

    @Test
    fun speechTextReadsGapsAndLetters() {
        assertEquals("I have lived here … 2019. A: since. B: for.", speechText("I have lived here ___ 2019.", listOf("since", "for")))
        // The gap is a pause between two spoken parts, never the word "blank".
        val parts = questionSpeech("Choose the correct form of 'be': She ___ happy.", listOf("be", "am"))
        assertEquals(listOf("Choose the correct form of 'be': She …", "happy."), parts.filter { it.key == "prompt" }.map { it.text })
        assertEquals(false, parts.any { "blank" in it.text || "_" in it.text })
        // A gap at the end of the sentence is just a pause before the options.
        assertEquals(listOf("I go to school by …"), questionSpeech("I go to school by ___.", listOf("bus")).filter { it.key == "prompt" }.map { it.text })
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
