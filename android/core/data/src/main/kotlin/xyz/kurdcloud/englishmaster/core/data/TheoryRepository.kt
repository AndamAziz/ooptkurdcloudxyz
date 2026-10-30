package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.database.TheoryDao
import xyz.kurdcloud.englishmaster.core.database.TheoryQuestionEntity
import xyz.kurdcloud.englishmaster.core.database.TheoryResponseEntity
import xyz.kurdcloud.englishmaster.core.database.TheorySectionEntity
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheoryQuestion
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTest
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.PracticeApi
import xyz.kurdcloud.englishmaster.core.network.SubmitTheoryResponseRequest
import xyz.kurdcloud.englishmaster.core.network.TheoryQuestionDto
import xyz.kurdcloud.englishmaster.core.network.TheoryQuestionsRequest
import xyz.kurdcloud.englishmaster.core.network.restCall
import java.util.UUID

/**
 * The driving theory course. Like practice it works offline: a chapter is downloaded once, answers
 * are written on the device first and sent on afterwards.
 *
 * Unlike practice, the app does the marking, because it builds the test options itself (the
 * questions carry only their correct answer). The server is told the outcome so progress follows
 * the learner to another device.
 */
interface TheoryRepository {
    /** The chapters with the learner's standing, from what is on the device. */
    fun observeSections(): Flow<List<TheorySection>>

    /** Questions saved on the device, per chapter slug: a chapter not listed works only online. */
    fun observeDownloadCounts(): Flow<Map<String, Int>>

    /** Refreshes the chapter list and each chapter's standing. */
    suspend fun refreshSections(): ApiResult<Int>

    /** Downloads one chapter's questions, replacing what was saved for it. */
    suspend fun downloadSection(slug: String): ApiResult<Int>

    /** A chapter's questions, hardest first: never seen, then got wrong, then known. */
    suspend fun deck(slug: String): List<TheoryQuestion>

    /** The whole chapter as a test, hardest first, each question with borrowed wrong options. */
    suspend fun testRound(slug: String): List<TheoryTestItem>

    /**
     * A mock exam drawn from the whole course: [TheoryTest.PER_SECTION] questions from each
     * chapter, shuffled into one paper of at most [TheoryTest.QUESTIONS].
     */
    suspend fun mockTest(): List<TheoryTestItem>

    /** How many questions are saved on the device, across every chapter. */
    suspend fun savedQuestionCount(): Int

    /** Records an answer locally and, when online, sends it straight on. */
    suspend fun record(question: TheoryQuestion, isCorrect: Boolean, mode: TheoryMode)

    /** Forgets the learner's answers in one chapter, on the device and on the server. */
    suspend fun resetSection(slug: String): ApiResult<Unit>

    /** Answers waiting to reach the server. */
    fun observePendingCount(): Flow<Int>

    /** Sends what is waiting; returns how many answers were accepted. */
    suspend fun syncNow(): Int
}

class OfflineFirstTheoryRepository(
    private val api: PracticeApi,
    private val dao: TheoryDao,
    private val network: NetworkMonitor,
    private val media: MediaFiles? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() },
) : TheoryRepository {

    override fun observeSections(): Flow<List<TheorySection>> =
        combine(dao.observeSections(), dao.observeStandings()) { sections, standings ->
            val local = standings.associateBy { it.sectionSlug }
            sections.map { row ->
                // Answers live on the device for good, so after a re-install the server's count is
                // the fuller record; while offline the device's is. The fuller of the two wins.
                val mine = local[row.slug]
                val useLocal = (mine?.answered ?: 0) >= row.serverAnswered
                TheorySection(
                    slug = row.slug,
                    nameEn = row.nameEn,
                    nameCkb = row.nameCkb,
                    published = row.published,
                    answered = if (useLocal) mine?.answered ?: 0 else row.serverAnswered,
                    correct = if (useLocal) mine?.correct ?: 0 else row.serverCorrect,
                )
            }
        }

    override fun observeDownloadCounts(): Flow<Map<String, Int>> =
        dao.observeDownloadCounts().map { rows -> rows.associate { it.sectionSlug to it.count } }

    override suspend fun refreshSections(): ApiResult<Int> {
        val rows = when (val r = restCall { api.getTheorySections() }) {
            is ApiResult.Success -> r.value
            is ApiResult.Failure -> return r
        }
        val now = nowMillis()
        dao.upsertSections(
            rows.map { TheorySectionEntity(it.slug, it.sortOrder, it.nameEn, it.nameCkb, it.published, it.answered, it.correct, now) },
        )
        // A chapter the server no longer publishes disappears from the app too.
        if (rows.isNotEmpty()) dao.removeSectionsOtherThan(rows.map { it.slug })
        return ApiResult.Success(rows.size)
    }

    override suspend fun downloadSection(slug: String): ApiResult<Int> {
        val collected = mutableListOf<TheoryQuestionDto>()
        var after: String? = null
        while (collected.size < MAX_QUESTIONS_PER_SECTION) {
            val page = when (val r = restCall { api.getTheoryQuestions(TheoryQuestionsRequest(slug, after, PAGE_SIZE)) }) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> return r
            }
            collected += page
            if (page.size < PAGE_SIZE) break
            after = page.last().id
        }
        val now = nowMillis()
        // Pictures are saved with the chapter so it works offline; one that fails to download is
        // shown from the network instead.
        dao.upsertQuestions(collected.map { it.toEntity(now, it.mediaPath?.let { path -> media?.cache(path) }) })
        // Questions the chapter no longer has go; answers to them stay, as the server keeps them too.
        if (collected.isNotEmpty()) dao.removeQuestionsOtherThan(slug, collected.map { it.id })
        return ApiResult.Success(collected.size)
    }

    override suspend fun deck(slug: String): List<TheoryQuestion> {
        val questions = dao.questionsOf(slug)
        val seen = dao.latestOf(questions.map { it.id }).associate { it.questionId to it.isCorrect }
        // Rank 0 never answered, 1 answered wrong, 2 answered right: study what is not known yet.
        return questions.sortedBy { q -> seen[q.id]?.let { if (it) 2 else 1 } ?: 0 }.map { it.toModel(media) }
    }

    override suspend fun testRound(slug: String): List<TheoryTestItem> {
        val chapter = dao.questionsOf(slug).map { it.toModel(media) }
        if (chapter.isEmpty()) return emptyList()
        return deck(slug).map { question -> chapter.testItem(question) }
    }

    /**
     * The paper is built chapter by chapter rather than from one big pile, so every sitting covers
     * the whole syllabus the way the real test does.
     *
     * Within a chapter the questions the learner has never met come first, then the ones they got
     * wrong, then the ones they know, and each of those groups is shuffled. So a weak area is
     * revisited without the learner ever sitting the same paper twice.
     *
     * Chapters are then taken from in rounds — one question from each, then a second from each —
     * so a course with more chapters than the paper has room for still ends up evenly spread.
     */
    override suspend fun mockTest(): List<TheoryTestItem> {
        val all = dao.allQuestions().map { it.toModel(media) }
        if (all.isEmpty()) return emptyList()
        val seen = dao.latestOf(all.map { it.id }).associate { it.questionId to it.isCorrect }
        val rank = { q: TheoryQuestion -> seen[q.id]?.let { if (it) 2 else 1 } ?: 0 }
        val bySection = all.groupBy { it.sectionSlug }
        val chapters = bySection.values.map { it.shuffled().sortedBy(rank) }

        val picked = LinkedHashSet<TheoryQuestion>()
        for (round in 0 until TheoryTest.PER_SECTION) {
            for (chapter in chapters) {
                if (picked.size >= TheoryTest.QUESTIONS) break
                chapter.getOrNull(round)?.let { picked += it }
            }
            if (picked.size >= TheoryTest.QUESTIONS) break
        }
        // Too few chapters to fill a paper: top up with the next-weakest questions, wherever they are.
        if (picked.size < TheoryTest.QUESTIONS) {
            chapters.flatMap { it.drop(TheoryTest.PER_SECTION) }.shuffled().sortedBy(rank)
                .forEach { if (picked.size < TheoryTest.QUESTIONS) picked += it }
        }
        return picked.shuffled().map { question ->
            // Wrong options come from the question's own chapter, so they stay plausible.
            bySection.getValue(question.sectionSlug).testItem(question)
        }
    }

    override suspend fun savedQuestionCount(): Int = dao.savedQuestionCount()

    /** Builds [question] into a test card, borrowing its wrong options from the rest of this list. */
    private fun List<TheoryQuestion>.testItem(question: TheoryQuestion) =
        TheoryTestItem.build(question, filter { it.id != question.id }.shuffled(), shuffle)

    override suspend fun record(question: TheoryQuestion, isCorrect: Boolean, mode: TheoryMode) {
        val row = TheoryResponseEntity(
            responseId = newId(),
            questionId = question.id,
            sectionSlug = question.sectionSlug,
            isCorrect = isCorrect,
            mode = mode.apiValue,
            answeredAtMillis = nowMillis(),
        )
        dao.insertResponse(row)
        if (network.isOnline.value) send(row)
    }

    override suspend fun resetSection(slug: String): ApiResult<Unit> {
        dao.clearSection(slug)
        val result = restCall { api.resetTheorySection(mapOf("p_section_slug" to slug)) }
        if (result is ApiResult.Success) refreshSections()
        return result
    }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun syncNow(): Int {
        if (!network.isOnline.value) return 0
        var sent = 0
        while (true) {
            val batch = dao.pendingResponses(SYNC_BATCH)
            if (batch.isEmpty()) break
            val done = batch.takeWhile { send(it) }
            sent += done.size
            if (done.size < batch.size) break
        }
        if (sent > 0) refreshSections()
        return sent
    }

    /** True when the answer reached the server (or can never reach it, so it stops blocking sync). */
    private suspend fun send(row: TheoryResponseEntity): Boolean {
        val result = restCall { api.submitTheoryResponse(SubmitTheoryResponseRequest(row.questionId, row.isCorrect, row.mode)) }
        return when (result) {
            is ApiResult.Success -> { dao.markSynced(listOf(row.responseId)); true }
            // A question the server has withdrawn can never be scored; drop the answer rather
            // than let it hold up every later one.
            is ApiResult.Failure -> if (result.error is ApiError.Validation) {
                dao.markSynced(listOf(row.responseId))
                true
            } else {
                false
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_QUESTIONS_PER_SECTION = 2_000
        const val SYNC_BATCH = 20
    }
}

private fun TheoryQuestionDto.toEntity(now: Long, mediaLocal: String?) = TheoryQuestionEntity(
    id = id,
    sectionSlug = sectionSlug,
    sortOrder = sortOrder,
    prompt = prompt,
    promptCkb = promptCkb,
    answer = answer,
    answerCkb = answerCkb,
    explanation = explanation,
    explanationCkb = explanationCkb,
    mediaPath = mediaPath,
    mediaLocal = mediaLocal,
    version = version,
    downloadedAtMillis = now,
)

internal fun TheoryQuestionEntity.toModel(media: MediaFiles?) = TheoryQuestion(
    id = id,
    sectionSlug = sectionSlug,
    prompt = prompt,
    promptCkb = promptCkb,
    answer = answer,
    answerCkb = answerCkb,
    explanation = explanation,
    explanationCkb = explanationCkb,
    media = questionMedia(MediaType.IMAGE.apiValue, mediaPath, null, mediaLocal, media),
    version = version,
)
