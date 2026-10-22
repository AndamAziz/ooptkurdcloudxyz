package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.model.ReviewSchedule.Answer

class ReviewScheduleTest {
    private val day = ReviewSchedule.FIRST_DELAY_MILLIS
    private fun a(correct: Boolean, at: Long) = Answer("q", correct, at)

    @Test fun rightFirstTimeIsNotAMistake() = assertNull(ReviewSchedule.mistakes(listOf(a(true, 0)))["q"])

    @Test fun aMistakeComesBackAfter24Hours() =
        assertEquals(ReviewSchedule.Mistake("q", 0, day), ReviewSchedule.mistakes(listOf(a(false, 0)))["q"])

    @Test fun answeringRightStraightAwayDoesNotFixIt() =
        assertEquals(0, ReviewSchedule.mistakes(listOf(a(false, 0), a(true, 60_000)))["q"]?.reviews)

    @Test fun twoRightReviewsOnLaterDaysFixIt() {
        val first = ReviewSchedule.mistakes(listOf(a(false, 0), a(true, day)))["q"]
        assertEquals(ReviewSchedule.Mistake("q", 1, day + 3 * day), first)
        assertNull(ReviewSchedule.mistakes(listOf(a(false, 0), a(true, day), a(true, 4 * day)))["q"])
    }

    @Test fun wrongWhenDueStartsAgainAndKnownCanBecomeAMistake() {
        assertEquals(ReviewSchedule.Mistake("q", 0, 5 * day), ReviewSchedule.mistakes(listOf(a(false, 0), a(true, day), a(false, 4 * day)))["q"])
        assertEquals(ReviewSchedule.Mistake("q", 0, 3 * day), ReviewSchedule.mistakes(listOf(a(true, 0), a(false, 2 * day)))["q"])
    }
}
