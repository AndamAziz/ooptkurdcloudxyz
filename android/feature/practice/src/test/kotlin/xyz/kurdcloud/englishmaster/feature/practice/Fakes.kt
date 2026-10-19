package xyz.kurdcloud.englishmaster.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncReport
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.optionLetter
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

fun question(id: String, correct: Int = 1) =
    Question(id, CefrLevel.A1, "grammar", "She ___ a teacher ($id).", listOf("am", "is", "are", "be"), 1, reviewed = false)

class FakeNetwork(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)
}

class FakeContent(var questions: List<Question> = emptyList()) : ContentRepository {
    val packs = MutableStateFlow<List<LevelPack>>(emptyList())
    var downloadResult: ApiResult<Int> = ApiResult.Success(3)
    var downloadGate: CompletableDeferred<Unit>? = null

    override fun observePacks(): Flow<List<LevelPack>> = packs
    override suspend fun downloadLevel(level: CefrLevel, onProgress: (Int) -> Unit): ApiResult<Int> {
        onProgress(1)
        downloadGate?.await()
        if (downloadResult is ApiResult.Success) packs.value = packs.value + LevelPack(level, 3, 0)
        return downloadResult
    }
    override suspend fun deleteLevel(level: CefrLevel) { packs.value = packs.value.filterNot { it.level == level } }
    override suspend fun pickSession(level: CefrLevel, userId: String, count: Int) = questions.take(count)
    override suspend fun questions(ids: List<String>) = questions.filter { it.id in ids }
}

/** Marks answers immediately when "online", otherwise leaves them pending until [completeSync]. */
class FakePractice(private val network: FakeNetwork) : PracticeRepository {
    private val rows = MutableStateFlow<List<PracticeAnswer>>(emptyList())
    private val correctOptions = mutableMapOf<String, String>()
    val pending = MutableStateFlow(0)
    var syncReport = SyncReport(0, 0, 0, null)

    override suspend fun answer(userId: String, attemptId: String, question: Question, optionIndex: Int): PracticeAnswer {
        val letter = optionLetter(optionIndex)
        correctOptions[question.id] = "B"
        val answer = if (network.isOnline.value) {
            PracticeAnswer("r-${question.id}", attemptId, question.id, letter, SyncState.SYNCED, letter == "B", "B", "Use is with she.", null)
        } else {
            PracticeAnswer("r-${question.id}", attemptId, question.id, letter, SyncState.PENDING, null, null, null, null)
        }
        rows.value = rows.value + answer
        return answer
    }

    fun completeSync() {
        rows.value = rows.value.map {
            if (it.syncState == SyncState.PENDING) it.copy(syncState = SyncState.SYNCED, isCorrect = it.chosenOption == "B", correctOption = "B") else it
        }
    }

    override fun observeAttempt(attemptId: String) = rows.map { list -> list.filter { it.attemptId == attemptId } }
    override fun observePendingCount(userId: String): Flow<Int> = pending
    override fun observeProgress(userId: String): Flow<List<LevelProgress>> = MutableStateFlow(emptyList())
    override suspend fun syncNow() = syncReport
    override fun newAttemptId() = "attempt-1"
}

val networkFailure: ApiResult<Int> = ApiResult.Failure(ApiError.Network)
