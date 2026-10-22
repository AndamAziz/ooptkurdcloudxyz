package xyz.kurdcloud.englishmaster.core.model

/** What a question pack holds: how many questions and the newest change among them. */
data class PackVersion(val level: CefrLevel, val questions: Int, val lastChangedAtMillis: Long?)

/**
 * True when the server's pack differs from the downloaded one: questions were added, retired
 * or edited since the download. [local] is null when the level was never downloaded.
 */
fun PackVersion.isNewerThan(local: PackVersion?): Boolean {
    if (local == null) return questions > 0
    if (questions != local.questions) return true
    val server = lastChangedAtMillis ?: return false
    return local.lastChangedAtMillis == null || server > local.lastChangedAtMillis
}

/** Progress and outcome of updating the downloaded questions. */
data class QuestionUpdateState(
    val running: Boolean = false,
    /** Started by the learner (shown with progress) rather than in the background. */
    val manual: Boolean = false,
    val current: CefrLevel? = null,
    val done: Int = 0,
    val total: Int = 0,
    val result: Result? = null,
    /** Questions in the packs that were refreshed by the last finished run. */
    val updatedQuestions: Int = 0,
    val finishedAtMillis: Long? = null,
) {
    enum class Result { UPDATED, UP_TO_DATE, OFFLINE, FAILED }
}

/** Placement tests left in the rolling 24 hours, and when the next one opens if none are left. */
data class PlacementStatus(val attemptsLeft: Int, val nextAttemptAtMillis: Long?, val inProgress: Boolean)
