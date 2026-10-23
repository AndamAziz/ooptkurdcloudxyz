package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.database.ResponseDao
import xyz.kurdcloud.englishmaster.core.database.ResponseEntity
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncReport
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.optionLetter
import java.util.UUID

interface PracticeRepository {
    /**
     * Records an answer locally first, then tries to get it scored immediately. When the
     * device is offline the answer stays PENDING and background sync is scheduled.
     */
    suspend fun answer(userId: String, attemptId: String, question: Question, optionIndex: Int): PracticeAnswer
    fun observeAttempt(attemptId: String): Flow<List<PracticeAnswer>>
    fun observePendingCount(userId: String): Flow<Int>
    fun observeProgress(userId: String): Flow<List<LevelProgress>>
    suspend fun syncNow(): SyncReport
    fun newAttemptId(): String = UUID.randomUUID().toString()
}

class OfflineFirstPracticeRepository(
    private val dao: ResponseDao,
    private val syncer: ResponseSyncer,
    private val scheduler: SyncScheduler,
    private val network: NetworkMonitor,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : PracticeRepository {

    override suspend fun answer(userId: String, attemptId: String, question: Question, optionIndex: Int): PracticeAnswer {
        val row = ResponseEntity(
            responseId = newId(),
            userId = userId,
            attemptId = attemptId,
            questionId = question.id,
            cefrLevel = question.level.apiValue,
            chosenOption = optionLetter(optionIndex),
            answeredAtMillis = nowMillis(),
            syncState = SyncState.PENDING.name,
        )
        dao.insert(row)
        val outcome = if (network.isOnline.value) syncer.syncOne(row) else null
        if (outcome == null || outcome is ResponseSyncer.Outcome.Stop) scheduler.requestSync()
        return checkNotNull(dao.byId(row.responseId)).toModel()
    }

    override fun observeAttempt(attemptId: String): Flow<List<PracticeAnswer>> =
        dao.observeAttempt(attemptId).map { rows -> rows.map { it.toModel() } }

    override fun observePendingCount(userId: String): Flow<Int> = dao.observePendingCount(userId)

    override fun observeProgress(userId: String): Flow<List<LevelProgress>> = dao.observeProgress(userId).map { rows ->
        rows.map { LevelProgress(CefrLevel.fromApi(it.cefrLevel), it.answered, it.correct) }
    }

    override suspend fun syncNow(): SyncReport = syncer.syncAll().also {
        if (it.stillPending > 0) scheduler.requestSync()
    }
}

internal fun ResponseEntity.toModel() = PracticeAnswer(
    responseId = responseId,
    attemptId = attemptId,
    questionId = questionId,
    chosenOption = chosenOption,
    syncState = SyncState.valueOf(syncState),
    isCorrect = isCorrect,
    correctOption = correctOption,
    explanation = explanation,
    failureReason = failureReason,
    explanationKu = explanationCkb,
)
