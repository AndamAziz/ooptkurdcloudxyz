package xyz.kurdcloud.englishmaster.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.data.TheoryRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncReport
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheoryQuestion
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem
import xyz.kurdcloud.englishmaster.core.model.Topic
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

    var topicQuestions: Map<Topic, List<Question>> = emptyMap()
    override suspend fun pickTopic(topic: Topic, userId: String, count: Int) = topicQuestions[topic].orEmpty().take(count)
    override fun observeTopicCounts(): Flow<Map<Topic, Int>> =
        packs.map { Topic.entries.associateWith { t -> topicQuestions[t].orEmpty().size } }

    var taggedQuestions: Map<String, List<Question>> = emptyMap()
    override suspend fun pickTagged(tag: String, userId: String, count: Int) = taggedQuestions[tag].orEmpty().take(count)
    override fun observeTagCounts(tags: List<String>): Flow<Map<String, Int>> =
        packs.map { tags.associateWith { t -> taggedQuestions[t].orEmpty().size } }
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
            PracticeAnswer("r-${question.id}", attemptId, question.id, letter, SyncState.SYNCED, letter == "B", "B", "Use is with she.", null,
                explanationKu = question.kurdish?.let { "لەگەڵ she فرمانی is بەکاردێت." })
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

fun theoryQuestion(id: String, answer: String = "Answer $id", section: String = "alertness") =
    TheoryQuestion(id, section, "What does $id mean?", "پرسیاری $id", answer, "وەڵامی $id", null, null, null, 1)

/** Keeps chapters and answers in memory; [online] decides whether a download finds anything. */
class FakeTheory(
    private val chapters: Map<String, List<TheoryQuestion>> = emptyMap(),
    sections: List<TheorySection> = emptyList(),
) : TheoryRepository {
    private val sectionRows = MutableStateFlow(sections)
    private val saved = MutableStateFlow(emptyMap<String, Int>())
    val recorded = mutableListOf<Triple<String, Boolean, TheoryMode>>()
    var refreshResult: ApiResult<Int> = ApiResult.Success(0)
    var downloadResult: ApiResult<Int>? = null
    var reset: String? = null

    override fun observeSections(): Flow<List<TheorySection>> = sectionRows
    override fun observeDownloadCounts(): Flow<Map<String, Int>> = saved
    override suspend fun refreshSections(): ApiResult<Int> = refreshResult
    override suspend fun downloadSection(slug: String): ApiResult<Int> {
        downloadResult?.let { return it }
        val count = chapters[slug].orEmpty().size
        saved.value = saved.value + (slug to count)
        return ApiResult.Success(count)
    }
    override suspend fun deck(slug: String) = chapters[slug].orEmpty().takeIf { saved.value.containsKey(slug) }.orEmpty()
    override suspend fun testRound(slug: String, count: Int) = deck(slug).take(count).map { q ->
        TheoryTestItem.build(q, deck(slug).filter { it.id != q.id }) { it }
    }
    override suspend fun record(question: TheoryQuestion, isCorrect: Boolean, mode: TheoryMode) {
        recorded += Triple(question.id, isCorrect, mode)
    }
    override suspend fun resetSection(slug: String): ApiResult<Unit> {
        reset = slug
        return ApiResult.Success(Unit)
    }
    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)
    override suspend fun syncNow(): Int = 0
}
