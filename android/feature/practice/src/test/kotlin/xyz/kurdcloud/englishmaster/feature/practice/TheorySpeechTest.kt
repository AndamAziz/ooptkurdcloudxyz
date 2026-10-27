package xyz.kurdcloud.englishmaster.feature.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TheorySpeechTest {
    @Test
    fun `reads the answer and then the explanation`() {
        val parts = theoryAnswerSpeech("Slow down and be ready to stop.", "A pedestrian may step out.")
        assertEquals(listOf("answer", "why"), parts.map { it.key })
        assertEquals("The answer is Slow down and be ready to stop.", parts[0].text)
        assertEquals("Why? A pedestrian may step out.", parts[1].text)
    }

    @Test
    fun `a question the learner answered correctly has only its explanation read back`() {
        val parts = theoryAnswerSpeech(null, "The sign warns of a bend.")
        assertEquals(listOf("why"), parts.map { it.key })
    }

    @Test
    fun `an answer with no explanation is read on its own`() {
        val parts = theoryAnswerSpeech("30 mph", null)
        assertEquals(listOf("answer"), parts.map { it.key })
        assertEquals("The answer is 30 mph.", parts[0].text)
    }

    @Test
    fun `a card with neither piece shows no listen button`() {
        assertTrue(theoryAnswerSpeech(null, "   ").isEmpty())
    }

    // A gap is a pause when spoken, never the word "underscore".
    @Test
    fun `a gap in the explanation becomes a pause`() {
        val parts = theoryAnswerSpeech(null, "You must ____ before turning.")
        assertEquals("Why? You must … before turning.", parts[0].text)
    }
}
