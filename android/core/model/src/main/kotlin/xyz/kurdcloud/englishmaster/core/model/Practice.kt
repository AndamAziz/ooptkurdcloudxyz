package xyz.kurdcloud.englishmaster.core.model

/** Lifecycle of a locally recorded answer. */
enum class SyncState {
    /** Saved on the device, not yet accepted by the server. */
    PENDING,

    /** Accepted and scored by the server. */
    SYNCED,

    /** Rejected permanently by the server (e.g. the question was withdrawn). */
    FAILED,
}

/** A learner's answer, with the server's verdict once it has been synchronised. */
data class PracticeAnswer(
    val responseId: String,
    val attemptId: String,
    val questionId: String,
    val chosenOption: String,
    val syncState: SyncState,
    val isCorrect: Boolean?,
    val correctOption: String?,
    val explanation: String?,
    val failureReason: String?,
)

/** Downloaded question pack for one level. */
data class LevelPack(val level: CefrLevel, val questionCount: Int, val downloadedAtMillis: Long)

data class LevelProgress(val level: CefrLevel, val answered: Int, val correct: Int)

data class SyncReport(val synced: Int, val stillPending: Int, val failed: Int, val stoppedReason: StopReason?) {
    enum class StopReason { OFFLINE, SESSION_EXPIRED, ACCOUNT_RESTRICTED, SERVER_UNAVAILABLE, NOT_SIGNED_IN }
}
