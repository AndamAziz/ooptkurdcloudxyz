package xyz.kurdcloud.englishmaster.core.model

/**
 * Spaced review of mistakes, the same rules the server applies (backend migration spaced_review):
 * a wrong answer brings the question back after 24 hours; a right answer then brings it back
 * after 3 days; a second right answer fixes it. Answers given before the review is due do not
 * count, so a mistake cannot be fixed by answering again straight after seeing the answer.
 */
object ReviewSchedule {
    const val FIRST_DELAY_MILLIS = 24 * 60 * 60 * 1000L
    const val SECOND_DELAY_MILLIS = 3 * 24 * 60 * 60 * 1000L

    data class Answer(val questionId: String, val correct: Boolean, val atMillis: Long)

    /** A question that is being learnt again after a mistake. */
    data class Mistake(val questionId: String, val reviews: Int, val dueAtMillis: Long) {
        fun isDue(nowMillis: Long) = dueAtMillis <= nowMillis
    }

    /** Questions currently being reviewed (known questions are left out). [answers] in any order. */
    fun mistakes(answers: List<Answer>): Map<String, Mistake> {
        val result = HashMap<String, Mistake>()
        for (a in answers.sortedBy { it.atMillis }) {
            val m = result[a.questionId]
            when {
                // Not under review (new or known): only a wrong answer changes anything.
                m == null -> if (!a.correct) result[a.questionId] = Mistake(a.questionId, 0, a.atMillis + FIRST_DELAY_MILLIS)
                a.atMillis < m.dueAtMillis -> Unit
                !a.correct -> result[a.questionId] = m.copy(reviews = 0, dueAtMillis = a.atMillis + FIRST_DELAY_MILLIS)
                m.reviews == 0 -> result[a.questionId] = m.copy(reviews = 1, dueAtMillis = a.atMillis + SECOND_DELAY_MILLIS)
                else -> result.remove(a.questionId)
            }
        }
        return result
    }
}

/** Mistakes of one level: how many can be reviewed now, how many wait, and when the next is due. */
data class MistakeStats(val due: Int, val waiting: Int, val nextDueAtMillis: Long?) {
    val total: Int get() = due + waiting

    companion object {
        val NONE = MistakeStats(0, 0, null)
    }
}
