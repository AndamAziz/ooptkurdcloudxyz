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

    @Test
    fun certificateWaitsForApprovalAfterAPass() {
        val o = LevelOverview(CefrLevel.A2, 40, 30, 28, 30, 70, true, null, false, 18, 20, null, null, certificatePending = true)
        assertEquals(LevelStage.AWAITING_APPROVAL, o.stage(0))
        assertEquals(LevelStage.CERTIFIED, o.copy(certificateCode = "EM-AAAA-BBBB-CCCC").stage(0))
        assertEquals(true, ExamResult(18, 20, true, 80, null, null, false).awaitingApproval)
        assertEquals(false, ExamResult(18, 20, true, 80, "EM-AAAA-BBBB-CCCC", null, false).awaitingApproval)
        assertEquals(false, ExamResult(10, 20, false, 80, null, 1L, false).awaitingApproval)
        assertEquals(ExamRefusal.PENDING_APPROVAL, ExamRefusal.fromHint("pending_approval"))
    }

    @Test
    fun onlyStaffApproveCertificates() {
        assertEquals(false, StaffAccess.from(setOf(Role.STUDENT)).canApproveCertificates)
        assertEquals(true, StaffAccess.from(setOf(Role.TEACHER)).canApproveCertificates)
        assertEquals(true, StaffAccess.from(setOf(Role.ADMIN)).canApproveCertificates)
    }
}
