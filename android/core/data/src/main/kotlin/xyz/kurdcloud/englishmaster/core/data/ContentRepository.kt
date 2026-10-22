package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.database.ContentDao
import xyz.kurdcloud.englishmaster.core.database.LevelPackDao
import xyz.kurdcloud.englishmaster.core.database.LevelPackEntity
import xyz.kurdcloud.englishmaster.core.database.QuestionDao
import xyz.kurdcloud.englishmaster.core.database.QuestionEntity
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.MistakeStats
import xyz.kurdcloud.englishmaster.core.model.ReviewSchedule
import xyz.kurdcloud.englishmaster.core.database.AnswerRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import xyz.kurdcloud.englishmaster.core.model.PackVersion
import xyz.kurdcloud.englishmaster.core.model.QuestionMedia
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.Topic
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.PracticeApi
import xyz.kurdcloud.englishmaster.core.network.PracticeQuestionDto
import xyz.kurdcloud.englishmaster.core.network.PracticeQuestionsRequest
import xyz.kurdcloud.englishmaster.core.network.restCall

/** Downloadable question packs. Practice runs entirely from downloaded content. */
interface ContentRepository {
    fun observePacks(): Flow<List<LevelPack>>

    /** Downloads a level's questions page by page and swaps them in atomically at the end. */
    suspend fun downloadLevel(level: CefrLevel, onProgress: (downloaded: Int) -> Unit = {}): ApiResult<Int>
    suspend fun deleteLevel(level: CefrLevel)
    suspend fun pickSession(level: CefrLevel, userId: String, count: Int): List<Question>
    suspend fun questions(ids: List<String>): List<Question>

    /** Mistakes whose review is due now (see [ReviewSchedule]), for a review session. */
    suspend fun pickMistakes(level: CefrLevel, userId: String, count: Int): List<Question> = emptyList()

    /** Mistakes that can be reviewed now, per level. */
    fun observeMistakeCounts(userId: String): Flow<Map<CefrLevel, Int>> =
        observeMistakeStats(userId).map { stats -> stats.mapValues { it.value.due }.filterValues { it > 0 } }

    /** Mistakes per level: due now, waiting, and when the next one is due. Refreshes every minute. */
    fun observeMistakeStats(userId: String): Flow<Map<CefrLevel, MistakeStats>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    /** Questions on one topic from every downloaded level. */
    suspend fun pickTopic(topic: Topic, userId: String, count: Int): List<Question> = emptyList()

    /** Downloaded questions per topic. */
    fun observeTopicCounts(): Flow<Map<Topic, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    /** Questions carrying one exact tag (e.g. a Life in the UK chapter), from every downloaded level. */
    suspend fun pickTagged(tag: String, userId: String, count: Int): List<Question> = emptyList()

    /** Downloaded questions per tag. */
    fun observeTagCounts(tags: List<String>): Flow<Map<String, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    /** What the server currently publishes, per level. */
    suspend fun serverVersions(): ApiResult<List<PackVersion>> = ApiResult.Success(emptyList())

    /** What is downloaded, per level (levels never downloaded are missing). */
    suspend fun localVersions(): List<PackVersion> = emptyList()
}

class OfflineFirstContentRepository(
    private val api: PracticeApi,
    private val questionDao: QuestionDao,
    private val packDao: LevelPackDao,
    private val contentDao: ContentDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val media: MediaFiles? = null,
) : ContentRepository {

    override fun observePacks(): Flow<List<LevelPack>> = packDao.observeAll().map { rows ->
        rows.map { LevelPack(CefrLevel.fromApi(it.cefrLevel), it.questionCount, it.downloadedAtMillis) }
    }

    override suspend fun downloadLevel(level: CefrLevel, onProgress: (Int) -> Unit): ApiResult<Int> {
        val collected = mutableListOf<PracticeQuestionDto>()
        var after: String? = null
        while (collected.size < MAX_QUESTIONS_PER_LEVEL) {
            val page = when (val r = restCall { api.getPracticeQuestions(PracticeQuestionsRequest(level.apiValue, after, PAGE_SIZE)) }) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> return r
            }
            collected += page
            onProgress(collected.size)
            if (page.size < PAGE_SIZE) break
            after = page.last().id
        }
        val now = nowMillis()
        // Pictures are saved with the pack so questions work offline; a picture that fails to
        // download is shown from the network instead.
        val entities = collected.map { dto ->
            val path = dto.mediaPath
            val local = if (dto.mediaType == MediaType.IMAGE.apiValue && path != null) media?.cache(path) else null
            dto.toEntity(now, local)
        }
        contentDao.replaceLevel(
            level.apiValue,
            entities,
            LevelPackEntity(level.apiValue, collected.size, now),
        )
        return ApiResult.Success(collected.size)
    }

    override suspend fun deleteLevel(level: CefrLevel) {
        questionDao.deleteLevel(level.apiValue)
        packDao.delete(level.apiValue)
    }

    override suspend fun pickSession(level: CefrLevel, userId: String, count: Int): List<Question> {
        val locked = lockedMistakes(userId)
        return questionDao.pickForSession(level.apiValue, userId, count + locked.size)
            .filter { it.id !in locked }.take(count).map { it.toModel(media) }
    }

    /** Mistakes waiting for their review: kept out of practice until they are due. */
    private suspend fun lockedMistakes(userId: String): Set<String> {
        val now = nowMillis()
        return ReviewSchedule.mistakes(questionDao.answerHistory(userId).map(AnswerRow::toAnswer))
            .values.filter { !it.isDue(now) }.mapTo(HashSet()) { it.questionId }
    }

    override suspend fun questions(ids: List<String>): List<Question> =
        questionDao.byIds(ids).map { it.toModel(media) }

    override suspend fun pickMistakes(level: CefrLevel, userId: String, count: Int): List<Question> {
        val now = nowMillis()
        val rows = questionDao.answerHistory(userId)
        val levelOf = rows.associate { it.questionId to it.cefrLevel }
        val due = ReviewSchedule.mistakes(rows.map(AnswerRow::toAnswer)).values
            .filter { it.isDue(now) && levelOf[it.questionId] == level.apiValue }
            .map { it.questionId }.shuffled().take(count)
        return questionDao.byIds(due).map { it.toModel(media) }
    }

    override fun observeMistakeStats(userId: String): Flow<Map<CefrLevel, MistakeStats>> =
        combine(questionDao.observeAnswerHistory(userId), minuteTicker()) { rows, _ -> mistakeStats(rows, nowMillis()) }

    private fun minuteTicker() = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    override suspend fun pickTopic(topic: Topic, userId: String, count: Int): List<Question> {
        val p = topic.likePatterns()
        val locked = lockedMistakes(userId)
        return questionDao.pickForTopic(p[0], p[1], p[2], p[3], topic.withPassage, topic.withMedia, userId, count + locked.size)
            .filter { it.id !in locked }.take(count).map { it.toModel(media) }
    }

    override fun observeTopicCounts(): Flow<Map<Topic, Int>> = combine(
        Topic.entries.map { topic ->
            val p = topic.likePatterns()
            questionDao.observeTopicCount(p[0], p[1], p[2], p[3], topic.withPassage, topic.withMedia).map { topic to it }
        },
    ) { pairs -> pairs.toMap() }

    override suspend fun pickTagged(tag: String, userId: String, count: Int): List<Question> {
        val locked = lockedMistakes(userId)
        return questionDao.pickForTopic(tagPattern(tag), "", "", "", false, false, userId, count + locked.size)
            .filter { it.id !in locked }.take(count).map { it.toModel(media) }
    }

    override fun observeTagCounts(tags: List<String>): Flow<Map<String, Int>> = combine(
        tags.map { tag -> questionDao.observeTopicCount(tagPattern(tag), "", "", "", false, false).map { tag to it } },
    ) { pairs -> pairs.toMap() }

    override suspend fun serverVersions(): ApiResult<List<PackVersion>> =
        restCall { api.getQuestionPackVersions() }.map { rows ->
            rows.mapNotNull { row ->
                val level = CefrLevel.entries.firstOrNull { it.apiValue == row.level } ?: return@mapNotNull null
                PackVersion(level, row.published, row.lastChangedAt?.let(::epochMillis))
            }
        }

    override suspend fun localVersions(): List<PackVersion> {
        val saved = questionDao.levelVersions().associateBy { it.cefrLevel }
        return packDao.all().mapNotNull { pack ->
            val level = CefrLevel.entries.firstOrNull { it.apiValue == pack.cefrLevel } ?: return@mapNotNull null
            val row = saved[pack.cefrLevel]
            PackVersion(level, row?.count ?: 0, row?.lastChanged?.let { runCatching { epochMillis(it) }.getOrNull() })
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_QUESTIONS_PER_LEVEL = 5_000
    }
}

/** Tags are stored as a JSON list, so `%"tag"%` matches one whole tag; "" never matches ("[]" at least). */
private fun Topic.likePatterns(): List<String> = tags.map(::tagPattern) + List(Topic.MAX_TAGS - tags.size) { "" }

private fun tagPattern(tag: String) = "%\"$tag\"%"

private fun PracticeQuestionDto.toEntity(now: Long, mediaLocal: String?) =
    QuestionEntity(id, level, skill, prompt, options, version, reviewStatus, updatedAt, now, passage, mediaType, mediaPath, mediaCaption, mediaLocal, tags)

internal fun QuestionEntity.toModel(media: MediaFiles? = null) = Question(
    id = id,
    level = CefrLevel.fromApi(cefrLevel),
    skill = skill,
    prompt = prompt,
    options = options,
    version = version,
    reviewed = reviewStatus == "approved",
    passage = passage,
    media = questionMedia(mediaType, mediaPath, mediaCaption, mediaLocal, media),
)

internal fun questionMedia(type: String?, path: String?, caption: String?, local: String?, media: MediaFiles?): QuestionMedia? {
    val t = MediaType.fromApi(type) ?: return null
    if (path == null) return null
    return QuestionMedia(t, media?.url(path) ?: path, caption, local)
}

private fun AnswerRow.toAnswer() = ReviewSchedule.Answer(questionId, isCorrect, answeredAtMillis)

internal fun mistakeStats(rows: List<AnswerRow>, nowMillis: Long): Map<CefrLevel, MistakeStats> {
    val levelOf = rows.associate { it.questionId to it.cefrLevel }
    return ReviewSchedule.mistakes(rows.map(AnswerRow::toAnswer)).values
        .groupBy { levelOf.getValue(it.questionId) }
        .mapNotNull { (level, list) ->
            val cefr = CefrLevel.entries.firstOrNull { it.apiValue == level } ?: return@mapNotNull null
            val waiting = list.filter { !it.isDue(nowMillis) }
            cefr to MistakeStats(list.size - waiting.size, waiting.size, waiting.minOfOrNull { it.dueAtMillis })
        }.toMap()
}
