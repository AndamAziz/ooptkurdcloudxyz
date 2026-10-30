package xyz.kurdcloud.englishmaster.core.designsystem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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

/** One piece of a reading (the question, option "A", the explanation...); [key] drives highlighting. */
data class SpeechPart(val key: String, val text: String)

/**
 * Reads English learning content aloud. [speakingId] is the item currently being read and
 * [speakingPart] the piece of it being spoken (Compose state, so buttons and options update by
 * themselves); [available] is false until the engine is ready with an English voice, and stays
 * false on devices without one. [autoRead] and [slow] are the learner's listening preferences.
 */
interface Speaker {
    val available: Boolean
    val speakingId: String?
    val speakingPart: String? get() = null
    val autoRead: Boolean get() = false
    val slow: Boolean get() = false
    fun speak(id: String, text: String)
    /** Reads [parts] one after another with a short pause between them. */
    fun speakParts(id: String, parts: List<SpeechPart>) = speak(id, parts.joinToString(" ") { it.text })
    fun stop()
    fun updateAutoRead(on: Boolean) {}
    fun updateSlow(on: Boolean) {}
}

/** Null where no speaker is provided (e.g. tests); [SpeakButton] then shows nothing. */
val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }

/**
 * Android text-to-speech with the best installed British English voice (US English as fallback).
 * Preferences are kept on the device. Call [shutdown] when done.
 */
class TextToSpeechSpeaker(context: Context) : Speaker {
    private val prefs = context.applicationContext.getSharedPreferences("speech", Context.MODE_PRIVATE)

    override var available by mutableStateOf(false)
        private set
    override var speakingId by mutableStateOf<String?>(null)
        private set
    override var speakingPart by mutableStateOf<String?>(null)
        private set
    override var autoRead by mutableStateOf(prefs.getBoolean(KEY_AUTO, false))
        private set
    override var slow by mutableStateOf(prefs.getBoolean(KEY_SLOW, false))
        private set

    /** Utterance id of the last part of the current reading; when it finishes the reading is over. */
    @Volatile private var lastUtterance: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onReady()
    }

    private fun onReady() {
        if (!chooseVoice()) return
        applyRate()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = started(utteranceId)
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId, stopped = true)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId, stopped = true)
        })
        available = true
    }

    /** Picks the highest-quality installed en-GB voice (offline voices first), else any English locale. */
    private fun chooseVoice(): Boolean {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty().filter { v ->
            v.locale.language == "en" && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
        }
        val best = voices.sortedWith(
            compareByDescending<Voice> { it.locale.country == "GB" }
                .thenBy { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.latency },
        ).firstOrNull()
        if (best != null && tts.setVoice(best) == TextToSpeech.SUCCESS) return true
        return listOf(Locale.UK, Locale.US).any { locale -> tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE }
    }

    // Slightly slower than normal speech is easier for learners; "slow" is for listening practice.
    private fun applyRate() {
        tts.setSpeechRate(if (slow) 0.72f else 0.92f)
        tts.setPitch(1.0f)
    }

    private fun started(utteranceId: String?) {
        val id = utteranceId ?: return
        val reading = speakingId ?: return
        if (id.startsWith("$reading$SEP") && !id.endsWith(GAP)) speakingPart = id.substringAfterLast(SEP)
    }

    private fun finished(utteranceId: String?, stopped: Boolean = false) {
        if (utteranceId == null) return
        if (stopped || utteranceId == lastUtterance) {
            if (utteranceId.startsWith("${speakingId}$SEP")) {
                speakingId = null
                speakingPart = null
            }
        }
    }

    override fun speak(id: String, text: String) = speakParts(id, listOf(SpeechPart("all", text)))

    override fun speakParts(id: String, parts: List<SpeechPart>) {
        if (!available || parts.isEmpty()) return
        tts.stop()
        speakingId = id
        speakingPart = null
        lastUtterance = "$id$SEP${parts.lastIndex}$SEP${parts.last().key}"
        parts.forEachIndexed { i, part ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (i > 0) tts.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_ADD, "$id$SEP$i$GAP")
            if (tts.speak(part.text, mode, null, "$id$SEP$i$SEP${part.key}") != TextToSpeech.SUCCESS) {
                speakingId = null
                speakingPart = null
                return
            }
        }
    }

    override fun stop() {
        speakingId = null
        speakingPart = null
        tts.stop()
    }

    override fun updateAutoRead(on: Boolean) {
        autoRead = on
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
        if (!on) stop()
    }

    override fun updateSlow(on: Boolean) {
        slow = on
        prefs.edit().putBoolean(KEY_SLOW, on).apply()
        applyRate()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val SEP = "#"
        const val GAP = "#gap"
        const val PAUSE_MS = 450L
        const val KEY_AUTO = "auto_read"
        const val KEY_SLOW = "slow"
    }
}

/**
 * Text for reading a multiple-choice question aloud, as one string: a gap ("___") becomes a
 * spoken pause ("…") and the options follow with their letters ("A: goes. B: go. ...").
 */
fun speechText(prompt: String, options: List<String>): String = questionSpeech(prompt, options).joinToString(" ") { it.text }

/**
 * The question and each option as separate parts (keys "prompt", "A", "B", ...), so options can
 * be highlighted as they are read. A gap is never read as a word: the prompt is split around it
 * and the speaker pauses there, like a teacher reading a fill-in-the-blank sentence.
 */
fun questionSpeech(prompt: String, options: List<String>): List<SpeechPart> {
    val trueFalse = prompt.startsWith("TRUE or FALSE:")
    var text = prompt.removePrefix("TRUE or FALSE:").replace(Regex("\\s+"), " ").trim()
    if (trueFalse) text = "True or false? $text"
    if (!text.endsWith('.') && !text.endsWith('?') && !text.endsWith('!') && !text.endsWith(':')) text += "."
    val hasGap = Regex("_{2,}").containsMatchIn(text)
    val pieces = text.split(Regex("_{2,}")).map { it.trim() }.filter { piece -> piece.any { it.isLetterOrDigit() } }
    val promptParts = if (pieces.size <= 1) {
        // No gap, or a gap at the very start or end: one part, with the pause marked where the gap was.
        val only = pieces.firstOrNull() ?: text
        listOf(SpeechPart("prompt", if (hasGap && text.trimEnd('.', '?', '!', ' ').endsWith("_")) "$only …" else only))
    } else {
        // "She" … "happy." — the pause between the parts stands for the gap.
        pieces.mapIndexed { i, piece -> SpeechPart("prompt", if (i < pieces.lastIndex) "$piece …" else piece) }
    }
    return promptParts + options.mapIndexed { i, option ->
        val letter = ('A' + i).toString()
        SpeechPart(letter, "$letter: ${option.trim().trimEnd('.')}.")
    }
}

/**
 * Small round "listen" button. Tap to hear [text]; while it plays the speaker turns into animated
 * sound bars and a tap stops it. Hidden when no English voice is available.
 */
@Composable
fun SpeakButton(id: String, text: String, modifier: Modifier = Modifier, size: Dp = 40.dp) =
    SpeakButton(id, listOf(SpeechPart("all", text)), modifier, size)

/** Like [SpeakButton] with text, but reads [parts] in order (options are highlighted as they are read). */
@Composable
fun SpeakButton(id: String, parts: List<SpeechPart>, modifier: Modifier = Modifier, size: Dp = 40.dp) {
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
        onClick = { if (speaking) speaker.stop() else speaker.speakParts(id, parts) },
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

/**
 * Listening bar shown above a question: a large Listen/Stop pill plus the learner's preferences —
 * read each question (and its explanation) automatically, and a slower voice. Hidden without a voice.
 */
@Composable
fun ListenControls(id: String, parts: List<SpeechPart>, modifier: Modifier = Modifier) {
    val speaker = LocalSpeaker.current ?: return
    if (!speaker.available) return
    val speaking = speaker.speakingId == id
    // Leaving the question — the next one, back, closing the test — stops its audio. Without this
    // the voice carries on reading a question the learner can no longer see.
    DisposableEffect(speaker, id) { onDispose { if (speaker.speakingId == id) speaker.stop() } }
    Row(
        modifier = modifier.fillMaxWidth().testTag("listen_controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = { if (speaking) speaker.stop() else speaker.speakParts(id, parts) },
            shape = CircleShape,
            color = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.testTag("listen_pill"),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (speaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                if (speaking) SoundBars(tint, Modifier.size(18.dp)) else SpeakerGlyph(tint, Modifier.size(20.dp))
                Text(
                    stringResource(if (speaking) R.string.ds_speak_stop_short else R.string.ds_speak_listen),
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        FilterChip(
            selected = speaker.autoRead,
            onClick = { speaker.updateAutoRead(!speaker.autoRead) },
            label = { Text(stringResource(R.string.ds_speak_auto)) },
            modifier = Modifier.testTag("listen_auto"),
        )
        FilterChip(
            selected = speaker.slow,
            onClick = { speaker.updateSlow(!speaker.slow) },
            label = { Text(stringResource(R.string.ds_speak_slow)) },
            modifier = Modifier.testTag("listen_slow"),
        )
    }
}
