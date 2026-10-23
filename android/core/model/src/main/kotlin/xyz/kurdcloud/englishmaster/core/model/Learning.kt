package xyz.kurdcloud.englishmaster.core.model

/**
 * Exam and certificate rules, mirrored from the server (private.exam_rules) for display only.
 * The server decides; these numbers just explain the rules to the learner.
 */
object ExamRules {
    const val EXAM_QUESTIONS = 40
    const val EXAM_MINUTES = 60
    const val PASS_PERCENT = 80
    const val RETRY_HOURS = 24
    const val PRACTICE_PERCENT = 70
    /** Share of a level's questions to answer before its exam unlocks. */
    const val PRACTICE_COVERAGE_PERCENT = 70
    const val PLACEMENT_QUESTIONS = 18
    const val PLACEMENT_MINUTES = 20
    /** Finished placement tests allowed in any 24 hours (tests left unfinished do not count). */
    const val PLACEMENT_PER_DAY = 3

    /** Checkpoints inside each level; all must be passed before the level exam unlocks. */
    const val CHECKPOINT_QUESTIONS = 15
    const val CHECKPOINT_MINUTES = 15
    const val CHECKPOINT_PASS_PERCENT = 70
    const val CHECKPOINT_RETRY_HOURS = 2
    /** Share of the level answered that opens checkpoint 1, 2 and 3. */
    val CHECKPOINT_COVERAGE = listOf(25, 50, 70)

    /** Placement stage 2: confirms the highest level the screening test says was passed. */
    const val CONFIRM_QUESTIONS = 20
    const val CONFIRM_MINUTES = 20
    const val CONFIRM_PASS_PERCENT = 70
}

/** Where a learner stands on the way to a level certificate. */
enum class LevelStage {
    /** Keep practising: not enough questions answered, or accuracy below the threshold. */
    PRACTISE,

    /** The final exam can be taken now. */
    EXAM_READY,

    /** An exam was started and its time has not run out. */
    EXAM_IN_PROGRESS,

    /** The last attempt failed; the next one opens at [LevelOverview.examAvailableAtMillis]. */
    WAIT_FOR_RETRY,

    /** The exam was passed; a teacher or admin still has to approve the certificate. */
    AWAITING_APPROVAL,

    /** A certificate has been issued (approved) for the level. */
    CERTIFIED,
}

/** Server-computed standing for one level (see get_learning_overview). */
data class LevelOverview(
    val level: CefrLevel,
    val published: Int,
    /** Distinct questions answered. */
    val answered: Int,
    /** Of those, how many the learner's latest answer got right. */
    val correct: Int,
    /** Distinct questions needed before the exam unlocks. */
    val required: Int,
    val requiredPercent: Int,
    val examUnlocked: Boolean,
    val examAvailableAtMillis: Long?,
    val examInProgress: Boolean,
    val lastExamScore: Int?,
    val lastExamTotal: Int?,
    val certificateCode: String?,
    val certificateIssuedAtMillis: Long?,
    /** Passed, waiting for a teacher/admin to approve the certificate. */
    val certificatePending: Boolean = false,
) {
    val accuracyPercent: Int get() = if (answered == 0) 0 else correct * 100 / answered
    val remainingToUnlock: Int get() = (required - answered).coerceAtLeast(0)
    val needsBetterAccuracy: Boolean get() = remainingToUnlock == 0 && !examUnlocked

    /** 0..1 progress towards unlocking the exam (by questions answered). */
    val unlockProgress: Float get() = if (required == 0) 0f else (answered.toFloat() / required).coerceIn(0f, 1f)

    fun stage(nowMillis: Long): LevelStage = when {
        certificateCode != null -> LevelStage.CERTIFIED
        certificatePending -> LevelStage.AWAITING_APPROVAL
        examInProgress -> LevelStage.EXAM_IN_PROGRESS
        examAvailableAtMillis != null && examAvailableAtMillis > nowMillis -> LevelStage.WAIT_FOR_RETRY
        examUnlocked -> LevelStage.EXAM_READY
        else -> LevelStage.PRACTISE
    }
}

data class LearnerProfile(
    val displayName: String,
    /** Recommended start level from the placement test; null if it was never taken. */
    val placementLevel: CefrLevel?,
    val totalAnswered: Int,
    val streakDays: Int,
)

data class ExamQuestion(
    val id: String,
    val level: CefrLevel,
    val prompt: String,
    val options: List<String>,
    val passage: String? = null,
    val media: QuestionMedia? = null,
)

enum class ExamKind { LEVEL_FINAL, PLACEMENT, CHECKPOINT, PLACEMENT_CONFIRM }

data class ExamSession(
    val attemptId: String,
    val kind: ExamKind,
    val level: CefrLevel?,
    val expiresAtMillis: Long,
    val questions: List<ExamQuestion>,
)

data class ExamResult(
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val passPercent: Int,
    val certificateCode: String?,
    val nextAttemptAtMillis: Long?,
    /** Submitted after the time limit (plus grace): recorded, but cannot pass. */
    val expired: Boolean,
) {
    val percent: Int get() = if (total == 0) 0 else score * 100 / total

    /** Passed: the certificate is issued once a teacher or admin approves it. */
    val awaitingApproval: Boolean get() = passed && certificateCode == null
}

data class PlacementResult(val score: Int, val total: Int, val passedLevel: CefrLevel?, val recommendedLevel: CefrLevel)

/** Placement stage 2: [confirmed] means the learner really has [testedLevel] and should study [recommendedLevel]. */
data class ConfirmationResult(val score: Int, val total: Int, val confirmed: Boolean, val testedLevel: CefrLevel, val recommendedLevel: CefrLevel) {
    val percent: Int get() = if (total == 0) 0 else score * 100 / total
}

/** One of a level's three checkpoints. */
data class CheckpointStatus(
    val level: CefrLevel,
    val number: Int,
    /** Distinct practice questions of the level to answer before it opens. */
    val required: Int,
    val unlocked: Boolean,
    val passed: Boolean,
    val lastScore: Int?,
    val lastTotal: Int?,
    /** After a failed try: when the next one opens. */
    val availableAtMillis: Long?,
    val inProgress: Boolean,
) {
    fun waiting(nowMillis: Long) = !passed && availableAtMillis != null && availableAtMillis > nowMillis
}

data class Certificate(
    val level: CefrLevel,
    val holderName: String,
    val score: Int,
    val total: Int,
    val code: String,
    val issuedAtMillis: Long,
) {
    /** Public page that the certificate's QR code opens. */
    fun verificationUrl(supabaseBaseUrl: String): String =
        supabaseBaseUrl.trimEnd('/') + "/functions/v1/verify-certificate?code=" + code
}

/** Why the server refused to start or submit an exam (from the RPC's error hint). */
enum class ExamRefusal { NOT_UNLOCKED, RETRY_LATER, ALREADY_CERTIFIED, PENDING_APPROVAL, NOT_ENOUGH_QUESTIONS, NOT_FOUND, ALREADY_DONE, OTHER;
    companion object {
        fun fromHint(hint: String?): ExamRefusal = when (hint) {
            "not_unlocked" -> NOT_UNLOCKED
            "retry_later" -> RETRY_LATER
            "already_certified" -> ALREADY_CERTIFIED
            "pending_approval" -> PENDING_APPROVAL
            "not_enough_questions" -> NOT_ENOUGH_QUESTIONS
            "not_found" -> NOT_FOUND
            "already_passed", "nothing_to_confirm" -> ALREADY_DONE
            else -> OTHER
        }
    }
}
