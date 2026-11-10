package xyz.kurdcloud.englishmaster.feature.practice

import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import xyz.kurdcloud.englishmaster.core.designsystem.EnglishMasterTheme
import xyz.kurdcloud.englishmaster.core.designsystem.QuestionMediaView
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.QuestionMedia

/**
 * A chapter of video questions shows several questions about the same clip and then moves on to
 * the next clip. The player on screen is reused between questions, and a VideoView keeps the clip
 * it was built with, so every question after the first one used to show the first clip.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionVideoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun video(view: View): VideoView? = when {
        view is VideoView -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { video(view.getChildAt(it)) }
        else -> null
    }

    private fun playing(): String? =
        video(compose.activity.window.decorView)?.let { shadowOf(it).videoURIString }

    @Test
    fun theNextQuestionsClipReplacesTheOneOnScreen() {
        var media by mutableStateOf(QuestionMedia(MediaType.VIDEO, CLIP_A, null))
        compose.setContent { EnglishMasterTheme { QuestionMediaView(media, "unavailable") } }
        compose.waitForIdle()
        assertEquals(CLIP_A, playing())

        // Another question about the same clip keeps playing it.
        media = QuestionMedia(MediaType.VIDEO, CLIP_A, null)
        compose.waitForIdle()
        assertEquals(CLIP_A, playing())

        // A question about the next clip plays that one instead.
        media = QuestionMedia(MediaType.VIDEO, CLIP_B, null)
        compose.waitForIdle()
        assertEquals(CLIP_B, playing())
    }

    private companion object {
        const val CLIP_A = "https://example.test/theory/clips/road-overtaking.mp4"
        const val CLIP_B = "https://example.test/theory/clips/town-roundabout.mp4"
    }
}
