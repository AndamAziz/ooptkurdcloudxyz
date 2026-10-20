package xyz.kurdcloud.englishmaster.core.designsystem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Reads English learning content aloud. [speakingId] is the item currently being read (Compose
 * state, so buttons update by themselves); [available] is false until the engine is ready with
 * an English voice, and stays false on devices without one.
 */
interface Speaker {
    val available: Boolean
    val speakingId: String?
    fun speak(id: String, text: String)
    fun stop()
}

/** Null where no speaker is provided (e.g. tests); [SpeakButton] then shows nothing. */
val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }

/** Android text-to-speech with a British English voice (US English as fallback). Call [shutdown] when done. */
class TextToSpeechSpeaker(context: Context) : Speaker {
    override var available by mutableStateOf(false)
        private set
    override var speakingId by mutableStateOf<String?>(null)
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onReady()
    }

    private fun onReady() {
        val ok = listOf(Locale.UK, Locale.US).any { locale ->
            tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
        if (!ok) return
        // A little slower than normal speech: easier to follow for learners.
        tts.setSpeechRate(0.9f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        available = true
    }

    private fun finished(utteranceId: String?) {
        if (speakingId == utteranceId) speakingId = null
    }

    override fun speak(id: String, text: String) {
        if (!available) return
        speakingId = id
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) speakingId = null
    }

    override fun stop() {
        speakingId = null
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}

/**
 * Text for reading a multiple-choice question aloud: the gap is read as "blank" and the options
 * follow with their letters ("A: goes. B: go. ...").
 */
fun speechText(prompt: String, options: List<String>): String = buildString {
    append(prompt.replace(Regex("_{2,}"), " blank ").replace(Regex("\\s+"), " ").trim())
    if (!endsWith('.') && !endsWith('?') && !endsWith('!') && !endsWith(':')) append('.')
    options.forEachIndexed { i, option -> append(' ').append('A' + i).append(": ").append(option.trim().trimEnd('.')).append('.') }
}

/**
 * Small round "listen" button. Tap to hear [text]; while it plays the speaker turns into animated
 * sound bars and a tap stops it. Hidden when no English voice is available.
 */
@Composable
fun SpeakButton(id: String, text: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val speaker = LocalSpeaker.current ?: return
    if (!speaker.available) return
    val speaking = speaker.speakingId == id
    // Leaving the question (next question, back, closing the exam) stops its audio.
    DisposableEffect(speaker, id) { onDispose { if (speaker.speakingId == id) speaker.stop() } }

    val container by animateColorAsState(
        if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, label = "speakBg",
    )
    val content = if (speaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val label = stringResource(if (speaking) R.string.ds_speak_stop else R.string.ds_speak_listen)
    Surface(
        onClick = { if (speaking) speaker.stop() else speaker.speak(id, text) },
        shape = CircleShape,
        color = container,
        shadowElevation = if (speaking) 4.dp else 0.dp,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = label; role = Role.Button }
            .testTag("speak_$id"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = speaking,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
                label = "speakIcon",
            ) { playing ->
                if (playing) SoundBars(content, Modifier.size(size * 0.5f)) else SpeakerGlyph(content, Modifier.size(size * 0.55f))
            }
        }
    }
}

/** Speaker with two sound waves, drawn so it needs no icon library. */
@Composable
private fun SpeakerGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.26f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.50f, h * 0.84f)
            lineTo(w * 0.26f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.62f)
            close()
        }
        drawPath(body, color)
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        drawArc(color, -45f, 90f, false, Offset(w * 0.40f, h * 0.30f), Size(w * 0.30f, h * 0.40f), style = stroke)
        drawArc(color, -50f, 100f, false, Offset(w * 0.40f, h * 0.12f), Size(w * 0.56f, h * 0.76f), style = stroke)
    }
}

/** Three bars moving like an equalizer while audio plays. */
@Composable
private fun SoundBars(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 160, 320).map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse, initialStartOffset = StartOffset(delay)),
            label = "bar$delay",
        )
    }
    Canvas(modifier) {
        val barWidth = size.width / 5f
        phases.forEachIndexed { i, phase ->
            val barHeight = size.height * phase.value
            drawRoundRect(
                color = color,
                topLeft = Offset(barWidth * (2 * i), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
