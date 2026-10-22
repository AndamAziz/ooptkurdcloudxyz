package xyz.kurdcloud.englishmaster.feature.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import xyz.kurdcloud.englishmaster.core.designsystem.FeedbackColors
import xyz.kurdcloud.englishmaster.core.designsystem.LearningType
import xyz.kurdcloud.englishmaster.core.designsystem.OptionCard
import xyz.kurdcloud.englishmaster.core.designsystem.OptionState
import xyz.kurdcloud.englishmaster.core.designsystem.LocalSpeaker
import xyz.kurdcloud.englishmaster.core.designsystem.SpeakButton
import xyz.kurdcloud.englishmaster.core.designsystem.SpeechPart
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncState

/** An explanation split into its handbook reference ("Handbook ch. 3 (A long ...)") and the text. */
data class ExplanationParts(val chapter: Int?, val chapterTitle: String?, val body: String)

private val HANDBOOK_PREFIX = Regex("""^Handbook ch\. (\d) \(([^)]*)\):\s*""")

fun explanationParts(explanation: String): ExplanationParts {
    val m = HANDBOOK_PREFIX.find(explanation) ?: return ExplanationParts(null, null, explanation.trim())
    return ExplanationParts(m.groupValues[1].toInt(), m.groupValues[2], explanation.substring(m.range.last + 1).trim())
}

/** Option text for display ("A" -> its option), or null when the letter is unknown. */
fun Question.optionFor(letter: String?): String? = letter?.singleOrNull()?.let { options.getOrNull(it - 'A') }

/** What is read aloud after an answer is marked: the verdict, the right answer and why. */
fun feedbackSpeech(question: Question, answer: PracticeAnswer): List<SpeechPart> {
    val correctLetter = answer.correctOption ?: return emptyList()
    val correct = "$correctLetter: ${question.optionFor(correctLetter).orEmpty()}."
    val parts = mutableListOf<SpeechPart>()
    if (answer.isCorrect == true) {
        parts += SpeechPart("verdict", "Correct! The answer is $correct")
    } else {
        parts += SpeechPart("verdict", "Not quite. You chose ${answer.chosenOption}: ${question.optionFor(answer.chosenOption).orEmpty()}.")
        parts += SpeechPart("correct", "The correct answer is $correct")
    }
    answer.explanation?.let { explanationParts(it).body }?.takeIf { it.isNotBlank() }?.let { parts += SpeechPart("why", "Why? $it") }
    return parts
}

/**
 * Feedback under a marked answer: verdict, the learner's answer and the correct one, why the
 * correct answer is right (with its handbook chapter), and a button to hear it all.
 */
@Composable
fun FeedbackCard(question: Question, answer: PracticeAnswer, modifier: Modifier = Modifier) {
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("feedback")
    when (answer.syncState) {
        SyncState.PENDING -> InfoCard(stringResource(R.string.practice_saved_pending), MaterialTheme.colorScheme.secondaryContainer, modifier.then(live))
        SyncState.FAILED -> InfoCard(stringResource(R.string.practice_rejected), MaterialTheme.colorScheme.errorContainer, modifier.then(live))
        SyncState.SYNCED -> MarkedFeedback(question, answer, modifier.then(live))
    }
}

@Composable
private fun InfoCard(text: String, color: Color, modifier: Modifier) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color), modifier = modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun MarkedFeedback(question: Question, answer: PracticeAnswer, modifier: Modifier) {
    val right = answer.isCorrect == true
    val tone = if (right) FeedbackColors.correct() else FeedbackColors.incorrect()
    val parts = answer.explanation?.let(::explanationParts)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, tone.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(tone, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (right) "✓" else "✗", color = Color.White, style = LearningType.heading)
                }
                Text(
                    stringResource(if (right) R.string.fb_correct_title else R.string.fb_wrong_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("feedback_title"),
                )
                if (LocalSpeaker.current != null) {
                    SpeakButton("feedback_${question.id}", feedbackSpeech(question, answer), size = 44.dp)
                }
            }
            // Learning content is English: keep it left-to-right inside the Sorani UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!right) {
                        AnswerLine(stringResource(R.string.fb_your_answer), answer.chosenOption, question.optionFor(answer.chosenOption), FeedbackColors.incorrect(), "✗")
                    }
                    AnswerLine(stringResource(R.string.fb_correct_answer), answer.correctOption, question.optionFor(answer.correctOption), FeedbackColors.correct(), "✓")
                }
            }
            if (parts != null && parts.body.isNotBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "💡  " + stringResource(if (right) R.string.fb_why_right else R.string.fb_why_wrong),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!right) {
                        Text(
                            stringResource(R.string.fb_why_wrong_body, question.optionFor(answer.chosenOption).orEmpty(), question.optionFor(answer.correctOption).orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(parts.body, style = LearningType.body, modifier = Modifier.testTag("feedback_explanation"))
                    }
                    if (parts.chapter != null) {
                        Text(
                            "📖  " + stringResource(R.string.fb_source, parts.chapter, parts.chapterTitle.orEmpty()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerLine(label: String, letter: String?, text: String?, color: Color, mark: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = Color.White, style = LearningType.label)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label.uppercase(), style = LearningType.label, color = color)
            Text("${letter.orEmpty()}.  ${text.orEmpty()}", style = LearningType.option)
        }
    }
}
