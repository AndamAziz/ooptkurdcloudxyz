package xyz.kurdcloud.englishmaster.core.model

/**
 * UK driving theory test: a course of its own beside English practice and Life in the UK.
 *
 * Its questions carry the correct answer but no wrong options, because they are studied to be
 * remembered. The app therefore offers two ways through the same question:
 *
 *  * [TheoryMode.STUDY] - the card shows the question, the learner thinks, then reveals the answer
 *    and says whether they knew it;
 *  * [TheoryMode.TEST] - the app makes four options by borrowing answers from other questions in
 *    the same section, so practice feels like the real multiple-choice exam.
 */
enum class TheoryMode { STUDY, TEST;

    val apiValue: String get() = name.lowercase()
}

/** Facts about the real DVSA car theory test, and the size of a round of practice in the app. */
object TheoryTest {
    const val QUESTIONS = 50
    const val MINUTES = 57
    const val PASS_MARK = 43

    /** The second part of the real test: hazard-perception video clips. */
    const val HAZARD_CLIPS = 14
    const val HAZARD_TOTAL = 75
    const val HAZARD_PASS_MARK = 44

    /**
     * How many questions a mock exam takes from each chapter. With the ten chapters the course
     * has, that is exactly the [QUESTIONS] of a real paper.
     */
    const val PER_SECTION = 5

    /** The pass mark for a paper of [total] questions, in proportion to the real one. */
    fun passMarkFor(total: Int): Int =
        if (total >= QUESTIONS) PASS_MARK else (total * PASS_MARK + QUESTIONS - 1) / QUESTIONS

    const val BOOK_URL = "https://www.gov.uk/book-theory-test"
    const val PRACTISE_URL = "https://www.gov.uk/take-practice-theory-test"
}

/** A chapter of the test. [published] is 0 while a chapter has no questions yet. */
data class TheorySection(
    val slug: String,
    val nameEn: String,
    val nameCkb: String?,
    val published: Int,
    val answered: Int,
    val correct: Int,
) {
    val isEmpty: Boolean get() = published == 0

    /** Percentage of the chapter's questions answered, 0 when it is still empty. */
    val answeredPercent: Int get() = if (published == 0) 0 else answered * 100 / published

    /** Percentage of answered questions that were right; 0 before anything is answered. */
    val accuracyPercent: Int get() = if (answered == 0) 0 else correct * 100 / answered

    val isComplete: Boolean get() = published > 0 && answered >= published
}

/** A theory question and its correct answer, with the Sorani translation when there is one. */
data class TheoryQuestion(
    val id: String,
    val sectionSlug: String,
    val prompt: String,
    val promptCkb: String?,
    val answer: String,
    val answerCkb: String?,
    val explanation: String?,
    val explanationCkb: String?,
    /** A sign or junction picture, for the questions that need one. */
    val media: QuestionMedia?,
    val version: Int,
)

/** One question as the test mode shows it: the answer among borrowed wrong options. */
data class TheoryTestItem(
    val question: TheoryQuestion,
    val options: List<String>,
    /** The Sorani text of each option, where the question it came from has one. */
    val optionsCkb: List<String?>,
    val correctIndex: Int,
) {
    companion object {
        /** How many options the test mode shows, the real exam's count. */
        const val OPTIONS = 4

        /** A study card: the question alone, with no options to choose from. */
        fun card(question: TheoryQuestion) = TheoryTestItem(question, emptyList(), emptyList(), -1)

        /**
         * Builds a question's options: its own answer plus up to three answers taken from
         * [others], questions of the same chapter. Answers that read the same are skipped, so no
         * option is a second correct one. With too few other questions the item simply has fewer
         * options.
         */
        fun build(question: TheoryQuestion, others: List<TheoryQuestion>, shuffle: (List<String>) -> List<String>): TheoryTestItem {
            val answer = question.answer.trim()
            val taken = linkedMapOf(answer to question.answerCkb)
            for (candidate in others) {
                if (taken.size >= OPTIONS) break
                val text = candidate.answer.trim()
                if (text.isNotEmpty() && taken.keys.none { it.equals(text, ignoreCase = true) }) taken[text] = candidate.answerCkb
            }
            val options = shuffle(taken.keys.toList())
            return TheoryTestItem(question, options, options.map { taken[it] }, options.indexOf(answer))
        }
    }
}

/**
 * A theory question a teacher is adding. There are no wrong options to write: the app builds them
 * from the other answers in the same chapter, so a teacher only types the question and its answer.
 */
data class NewTheoryQuestion(
    val sectionSlug: String,
    val prompt: String,
    val answer: String,
    val promptKu: String? = null,
    val answerKu: String? = null,
    val explanation: String? = null,
    val explanationKu: String? = null,
    /** Object path of an uploaded picture in the question-media bucket. */
    val mediaPath: String? = null,
    /** False keeps it a draft, so another teacher can check it first. */
    val publish: Boolean = false,
) {
    val isValid: Boolean
        get() = sectionSlug.isNotBlank() && prompt.trim().length in 1..2000 && answer.trim().length in 1..1000
}
