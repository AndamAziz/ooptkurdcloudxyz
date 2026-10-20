package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.kurdcloud.englishmaster.core.database.ContentDao
import xyz.kurdcloud.englishmaster.core.database.LevelPackDao
import xyz.kurdcloud.englishmaster.core.database.LevelPackEntity
import xyz.kurdcloud.englishmaster.core.database.QuestionDao
import xyz.kurdcloud.englishmaster.core.database.QuestionEntity
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.MediaType
import xyz.kurdcloud.englishmaster.core.model.QuestionMedia
import xyz.kurdcloud.englishmaster.core.model.Question
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

    /** Downloaded questions whose latest marked answer was wrong, for a review session. */
    suspend fun pickMistakes(level: CefrLevel, userId: String, count: Int): List<Question> = emptyList()
    fun observeMistakeCounts(userId: String): Flow<Map<CefrLevel, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())
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

    override suspend fun pickSession(level: CefrLevel, userId: String, count: Int): List<Question> =
        questionDao.pickForSession(level.apiValue, userId, count).map { it.toModel(media) }

    override suspend fun questions(ids: List<String>): List<Question> =
        questionDao.byIds(ids).map { it.toModel(media) }

    override suspend fun pickMistakes(level: CefrLevel, userId: String, count: Int): List<Question> =
        questionDao.pickMistakes(level.apiValue, userId, count).map { it.toModel(media) }

    override fun observeMistakeCounts(userId: String): Flow<Map<CefrLevel, Int>> =
        questionDao.observeMistakeCounts(userId).map { rows -> rows.associate { CefrLevel.fromApi(it.cefrLevel) to it.count } }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_QUESTIONS_PER_LEVEL = 5_000
    }
}

private fun PracticeQuestionDto.toEntity(now: Long, mediaLocal: String?) =
    QuestionEntity(id, level, skill, prompt, options, version, reviewStatus, updatedAt, now, passage, mediaType, mediaPath, mediaCaption, mediaLocal)

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
