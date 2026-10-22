package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionUpdatesTest {
    private fun v(n: Int, at: Long?) = PackVersion(CefrLevel.A1, n, at)

    @Test fun samePackIsNotNewer() = assertFalse(v(10, 500).isNewerThan(v(10, 500)))
    @Test fun addedOrRetiredQuestionsMakeItNewer() {
        assertTrue(v(11, 500).isNewerThan(v(10, 500)))
        assertTrue(v(9, 500).isNewerThan(v(10, 500)))
    }
    @Test fun anEditMakesItNewer() = assertTrue(v(10, 900).isNewerThan(v(10, 500)))
    @Test fun aNeverDownloadedLevelIsNewerOnlyWithQuestions() {
        assertTrue(v(3, 1).isNewerThan(null))
        assertFalse(v(0, null).isNewerThan(null))
    }
}
