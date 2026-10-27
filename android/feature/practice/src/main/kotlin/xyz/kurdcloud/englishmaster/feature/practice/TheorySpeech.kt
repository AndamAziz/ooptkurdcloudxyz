package xyz.kurdcloud.englishmaster.feature.practice

import xyz.kurdcloud.englishmaster.core.designsystem.SpeechPart

/**
 * What the speaker reads from a theory answer card: the correct answer, then the explanation.
 *
 * [answer] is null in test mode when the learner was right, because the card then shows the
 * explanation alone — the reading follows what is on screen rather than repeating an answer the
 * learner already chose. Either piece may be missing, so the result can be empty and the card
 * then shows no listen button.
 */
fun theoryAnswerSpeech(answer: String?, explanation: String?): List<SpeechPart> {
    val parts = mutableListOf<SpeechPart>()
    answer?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        ?.let { parts += SpeechPart("answer", "The answer is $it.") }
    explanation?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { parts += SpeechPart("why", "Why? " + it.replace(Regex("_{2,}"), "…")) }
    return parts
}
