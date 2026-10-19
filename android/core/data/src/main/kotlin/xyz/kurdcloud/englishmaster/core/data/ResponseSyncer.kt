package xyz.kurdcloud.englishmaster.core.data

import xyz.kurdcloud.englishmaster.core.database.ResponseDao
import xyz.kurdcloud.englishmaster.core.database.ResponseEntity
import xyz.kurdcloud.englishmaster.core.model.SyncReport
import xyz.kurdcloud.englishmaster.core.model.SyncReport.StopReason
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.PracticeApi
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SubmitResponseRequest
import xyz.kurdcloud.englishmaster.core.network.restCall
import java.time.Instant

/**
 * Uploads locally stored answers. Safe to run repeatedly and concurrently with new answers:
 * every upload carries the device-generated response id, which the server uses as an
 * idempotency key, so a retry after a lost response never creates a duplicate.
 *
 * Conflict rule: the server keeps the first answer stored for a question within an attempt;
 * the local row is updated with whatever verdict the server holds.
 */
class ResponseSyncer(
    private val api: PracticeApi,
    private val dao: ResponseDao,
    private val sessions: SessionProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data object Synced : Outcome
        data object Rejected : Outcome
        data class Stop(val reason: StopReason) : Outcome
    }

    suspend fun syncOne(row: ResponseEntity): Outcome {
        val request = SubmitResponseRequest(
            responseId = row.responseId,
            attemptId = row.attemptId,
            questionId = row.questionId,
            chosenOption = row.chosenOption,
            clientAnsweredAt = Instant.ofEpochMilli(row.answeredAtMillis).toString(),
        )
        return when (val result = restCall { api.submitPracticeResponse(request) }) {
            is ApiResult.Success -> {
                val verdict = result.value.singleOrNull()
                if (verdict == null) {
                    dao.countSyncAttempt(row.responseId)
                    Outcome.Stop(StopReason.SERVER_UNAVAILABLE)
                } else {
                    dao.markSynced(row.responseId, verdict.isCorrect, verdict.correctOption, verdict.explanation, nowMillis())
                    Outcome.Synced
                }
            }
            is ApiResult.Failure -> when (val e = result.error) {
                is ApiError.Validation -> {
                    dao.markFailed(row.responseId, "rejected:${e.code ?: "unknown"}")
                    Outcome.Rejected
                }
                ApiError.Network -> { dao.countSyncAttempt(row.responseId); Outcome.Stop(StopReason.OFFLINE) }
                ApiError.Unauthorized -> Outcome.Stop(StopReason.SESSION_EXPIRED)
                ApiError.Forbidden -> Outcome.Stop(StopReason.ACCOUNT_RESTRICTED)
                ApiError.NotConfigured -> Outcome.Stop(StopReason.SERVER_UNAVAILABLE)
                ApiError.RateLimited, is ApiError.Server, is ApiError.Auth -> {
                    dao.countSyncAttempt(row.responseId)
                    Outcome.Stop(StopReason.SERVER_UNAVAILABLE)
                }
            }
        }
    }

    /** Uploads all pending answers of the signed-in user, oldest first. */
    suspend fun syncAll(): SyncReport {
        val userId = sessions.current()?.userId
            ?: return SyncReport(0, 0, 0, StopReason.NOT_SIGNED_IN)
        var synced = 0
        var failed = 0
        while (true) {
            val batch = dao.pending(userId, BATCH_SIZE)
            if (batch.isEmpty()) return SyncReport(synced, 0, failed, null)
            for (row in batch) {
                when (val outcome = syncOne(row)) {
                    Outcome.Synced -> synced++
                    Outcome.Rejected -> failed++
                    is Outcome.Stop -> return SyncReport(synced, dao.pending(userId, Int.MAX_VALUE).size, failed, outcome.reason)
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}
