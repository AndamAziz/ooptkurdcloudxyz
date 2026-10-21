package xyz.kurdcloud.englishmaster.core.model

/**
 * Preparation for the official Life in the UK test. The questions are original practice items
 * based on the official handbook "Life in the United Kingdom: A Guide for New Residents"; the
 * real test's questions are not public.
 */
object LifeInUkTest {
    /** Format of the official test: 24 questions in 45 minutes, pass mark 75% (18 correct). */
    const val QUESTIONS = 24
    const val MINUTES = 45
    const val PASS_MARK = 18

    /** Tag every Life in the UK question carries. */
    const val TAG = "life_in_uk"

    /** Booking and rules (GOV.UK). */
    const val OFFICIAL_TEST_URL = "https://www.gov.uk/life-in-the-uk-test"
    const val TEST_DAY_URL = "https://www.gov.uk/life-in-the-uk-test/what-happens-test"
    /** Where GOV.UK sends people for the official handbook. */
    const val HANDBOOK_URL = "https://www.officiallifeintheuk.co.uk/shop"
}

/** Handbook chapters; each question carries one chapter tag. */
enum class LifeInUkChapter(val number: Int, val tag: String) {
    VALUES(1, "lituk_values"),
    WHAT_IS_UK(2, "lituk_what_is_uk"),
    HISTORY(3, "lituk_history"),
    SOCIETY(4, "lituk_society"),
    GOVERNMENT(5, "lituk_government");

    companion object {
        fun fromTag(tag: String?): LifeInUkChapter? = entries.firstOrNull { it.tag == tag }
    }
}
