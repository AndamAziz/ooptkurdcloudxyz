package xyz.kurdcloud.englishmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    private fun overview(
        answered: Int = 0,
        correct: Int = 0,
        unlocked: Boolean = false,
        availableAt: Long? = null,
        inProgress: Boolean = false,
        certificate: String? = null,
    ) = LevelOverview(CefrLevel.B1, 41, answered, correct, 30, 70, unlocked, availableAt, inProgress, null, null, certificate, null)

    @Test
    fun stagesFollowTheServerFlags() {
        assertEquals(LevelStage.PRACTISE, overview(answered = 10, correct = 9).stage(0))
        assertEquals(LevelStage.EXAM_READY, overview(answered = 30, correct = 25, unlocked = true).stage(0))
        assertEquals(LevelStage.EXAM_IN_PROGRESS, overview(unlocked = true, inProgress = true).stage(0))
        assertEquals(LevelStage.WAIT_FOR_RETRY, overview(unlocked = true, availableAt = 5_000).stage(1_000))
        assertEquals(LevelStage.EXAM_READY, overview(unlocked = true, availableAt = 5_000).stage(6_000))
        assertEquals(LevelStage.CERTIFIED, overview(unlocked = true, certificate = "EM-AAAA-BBBB-CCCC").stage(0))
    }

    @Test
    fun progressTowardsUnlocking() {
        val o = overview(answered = 22, correct = 14)
        assertEquals(8, o.remainingToUnlock)
        assertEquals(63, o.accuracyPercent)
        assertEquals(22f / 30, o.unlockProgress, 0.001f)
        assertTrue(overview(answered = 30, correct = 15).needsBetterAccuracy)
    }

    @Test
    fun certificateVerificationUrlAndRefusalHints() {
        val c = Certificate(CefrLevel.A1, "Sara", 18, 20, "EM-ABCD-EFGH-JKLM", 0)
        assertEquals("https://x.supabase.co/functions/v1/verify-certificate?code=EM-ABCD-EFGH-JKLM", c.verificationUrl("https://x.supabase.co/"))
        assertEquals(ExamRefusal.RETRY_LATER, ExamRefusal.fromHint("retry_later"))
        assertEquals(ExamRefusal.OTHER, ExamRefusal.fromHint(null))
    }
}
