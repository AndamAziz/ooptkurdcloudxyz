package xyz.kurdcloud.englishmaster.core.data

import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.model.CheckpointStatus
import xyz.kurdcloud.englishmaster.core.model.ConfirmationResult
import xyz.kurdcloud.englishmaster.core.network.StartCheckpointRequest
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.model.ExamQuestion
import xyz.kurdcloud.englishmaster.core.model.ExamResult
import xyz.kurdcloud.englishmaster.core.model.ExamSession
import xyz.kurdcloud.englishmaster.core.model.LearnerProfile
import xyz.kurdcloud.englishmaster.core.model.LevelOverview
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.model.PlacementStatus
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.ExamStartDto
import xyz.kurdcloud.englishmaster.core.network.LearningApi
import xyz.kurdcloud.englishmaster.core.network.StartLevelExamRequest
import xyz.kurdcloud.englishmaster.core.network.SubmitExamRequest
import xyz.kurdcloud.englishmaster.core.network.UpdateProfileRequest
import xyz.kurdcloud.englishmaster.core.network.restCall
import java.time.OffsetDateTime

/**
 * Progress, exams and certificates. Online only: exams are run and scored by the server, so
 * nothing here works offline (practice does).
 */
interface LearningRepository {
    suspend fun overview(): ApiResult<List<LevelOverview>>
    suspend fun profile(): ApiResult<LearnerProfile>
    suspend fun updateDisplayName(name: String): ApiResult<String>
    suspend fun startLevelExam(level: CefrLevel): ApiResult<ExamSession>
    /** [answers] maps question id to the chosen option letter; unanswered questions count as wrong. */
    suspend fun submitLevelExam(attemptId: String, answers: Map<String, String>): ApiResult<ExamResult>
    suspend fun startPlacement(): ApiResult<ExamSession>
    suspend fun submitPlacement(attemptId: String, answers: Map<String, String>): ApiResult<PlacementResult>
    suspend fun certificates(): ApiResult<List<Certificate>>

    /** Placement tests left today and when the next one opens. */
    suspend fun placementStatus(): ApiResult<PlacementStatus> = ApiResult.Failure(ApiError.NotConfigured)

    /** The three checkpoints of every English level. */
    suspend fun checkpoints(): ApiResult<List<CheckpointStatus>> = ApiResult.Failure(ApiError.NotConfigured)
    suspend fun startCheckpoint(level: CefrLevel, number: Int): ApiResult<ExamSession> = ApiResult.Failure(ApiError.NotConfigured)
    suspend fun submitCheckpoint(attemptId: String, answers: Map<String, String>): ApiResult<ExamResult> =
        ApiResult.Failure(ApiError.NotConfigured)

    /** Placement stage 2: a confirmation test at the level the screening test says was passed. */
    suspend fun startPlacementConfirmation(): ApiResult<ExamSession> = ApiResult.Failure(ApiError.NotConfigured)
    suspend fun submitPlacementConfirmation(attemptId: String, answers: Map<String, String>): ApiResult<ConfirmationResult> =
        ApiResult.Failure(ApiError.NotConfigured)
}

class SupabaseLearningRepository(private val api: LearningApi, private val media: MediaFiles? = null) : LearningRepository {

    override suspend fun overview() = restCall { api.overview() }.map { rows ->
        rows.map {
            LevelOverview(
                level = CefrLevel.fromApi(it.level),
                published = it.published,
                answered = it.answered,
                correct = it.correct,
                required = it.required,
                requiredPercent = it.requiredPercent,
                examUnlocked = it.examUnlocked,
                examAvailableAtMillis = it.examAvailableAt?.let(::epochMillis),
                examInProgress = it.examInProgress != null,
                lastExamScore = it.lastExamScore,
                lastExamTotal = it.lastExamTotal,
                certificateCode = it.certificateCode,
                certificateIssuedAtMillis = it.certificateIssuedAt?.let(::epochMillis),
                certificatePending = it.certificateStatus == "pending",
            )
        }
    }

    override suspend fun profile() = restCall { api.profile() }.map { rows ->
        val p = rows.single()
        LearnerProfile(p.displayName, p.placementLevel?.let(CefrLevel::fromApi), p.totalAnswered, p.streakDays)
    }

    override suspend fun updateDisplayName(name: String) =
        restCall { api.updateProfile(UpdateProfileRequest(name.trim())) }.map { it.displayName }

    override suspend fun startLevelExam(level: CefrLevel) =
        restCall { api.startLevelExam(StartLevelExamRequest(level.apiValue)) }.map { it.single().toSession(ExamKind.LEVEL_FINAL, media) }

    override suspend fun submitLevelExam(attemptId: String, answers: Map<String, String>) =
        restCall { api.submitLevelExam(SubmitExamRequest(attemptId, answers)) }.map { rows ->
            val r = rows.single()
            ExamResult(r.score, r.total, r.passed, r.passPercent, r.certificateCode, r.nextAttemptAt?.let(::epochMillis), r.expired)
        }

    override suspend fun startPlacement() =
        restCall { api.startPlacement() }.map { it.single().toSession(ExamKind.PLACEMENT, media) }

    override suspend fun submitPlacement(attemptId: String, answers: Map<String, String>) =
        restCall { api.submitPlacement(SubmitExamRequest(attemptId, answers)) }.map { rows ->
            val r = rows.single()
            PlacementResult(r.score, r.total, r.passedLevel?.let(CefrLevel::fromApi), CefrLevel.fromApi(r.recommendedLevel))
        }

    override suspend fun placementStatus() = restCall { api.placementStatus() }.map { rows ->
        val r = rows.single()
        PlacementStatus(
            r.attemptsLeft, r.nextAttemptAt?.let(::epochMillis), r.inProgress,
            r.confirmLevel?.let(CefrLevel::fromApi), r.confirmInProgress, r.confirmed,
        )
    }

    override suspend fun checkpoints() = restCall { api.checkpoints() }.map { rows ->
        rows.map {
            CheckpointStatus(
                CefrLevel.fromApi(it.level), it.checkpoint, it.required, it.unlocked, it.passed,
                it.lastScore, it.lastTotal, it.availableAt?.let(::epochMillis), it.inProgress != null,
            )
        }
    }

    override suspend fun startCheckpoint(level: CefrLevel, number: Int) =
        restCall { api.startCheckpoint(StartCheckpointRequest(level.apiValue, number)) }.map { it.single().toSession(ExamKind.CHECKPOINT, media) }

    override suspend fun submitCheckpoint(attemptId: String, answers: Map<String, String>) =
        restCall { api.submitCheckpoint(SubmitExamRequest(attemptId, answers)) }.map { rows ->
            val r = rows.single()
            ExamResult(r.score, r.total, r.passed, r.passPercent, null, r.nextAttemptAt?.let(::epochMillis), r.expired)
        }

    override suspend fun startPlacementConfirmation() =
        restCall { api.startPlacementConfirmation() }.map { it.single().toSession(ExamKind.PLACEMENT_CONFIRM, media) }

    override suspend fun submitPlacementConfirmation(attemptId: String, answers: Map<String, String>) =
        restCall { api.submitPlacementConfirmation(SubmitExamRequest(attemptId, answers)) }.map { rows ->
            val r = rows.single()
            ConfirmationResult(r.score, r.total, r.confirmed, CefrLevel.fromApi(r.testedLevel), CefrLevel.fromApi(r.recommendedLevel))
        }

    override suspend fun certificates() = restCall { api.certificates() }.map { rows ->
        rows.map { Certificate(CefrLevel.fromApi(it.level), it.holderName, it.score, it.total, it.code, epochMillis(it.issuedAt)) }
    }
}

private fun ExamStartDto.toSession(kind: ExamKind, media: MediaFiles?) = ExamSession(
    attemptId = attemptId,
    kind = kind,
    level = level?.let(CefrLevel::fromApi),
    expiresAtMillis = epochMillis(expiresAt),
    questions = questions.map {
        ExamQuestion(
            it.id, CefrLevel.fromApi(it.level), it.prompt, it.options, it.passage,
            questionMedia(it.mediaType, it.mediaPath, it.mediaCaption, null, media),
        )
    },
)

/** PostgREST timestamps are ISO-8601 with an offset, e.g. 2026-10-20T03:00:00.123+00:00. */
internal fun epochMillis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

