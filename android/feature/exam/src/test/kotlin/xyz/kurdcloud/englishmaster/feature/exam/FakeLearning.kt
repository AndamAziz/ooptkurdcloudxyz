package xyz.kurdcloud.englishmaster.feature.exam

import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.model.Certificate
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.model.ExamQuestion
import xyz.kurdcloud.englishmaster.core.model.ExamResult
import xyz.kurdcloud.englishmaster.core.model.ExamSession
import xyz.kurdcloud.englishmaster.core.model.LearnerProfile
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

fun examQuestion(id: String) = ExamQuestion(id, CefrLevel.A1, "He ___ happy ($id).", listOf("are", "is", "am", "be"), )

/** Scores "B" as correct for every question. */
class FakeLearning(var questions: List<ExamQuestion> = listOf(examQuestion("q1"), examQuestion("q2")), var expiresAt: Long = 30 * 60_000L) :
    LearningRepository {
    var startFailure: ApiError? = null
    var submitFailure: ApiError? = null
    var submitted: Map<String, String>? = null
    var name = "Sara Ahmed"

    override suspend fun overview() = ApiResult.Success(emptyList<xyz.kurdcloud.englishmaster.core.model.LevelOverview>())
    override suspend fun profile() = ApiResult.Success(LearnerProfile(name, null, 0, 0))
    override suspend fun updateDisplayName(name: String): ApiResult<String> { this.name = name.trim(); return ApiResult.Success(this.name) }

    private fun session(kind: ExamKind): ApiResult<ExamSession> =
        startFailure?.let { ApiResult.Failure(it) } ?: ApiResult.Success(ExamSession("att", kind, CefrLevel.A1, expiresAt, questions))

    override suspend fun startLevelExam(level: CefrLevel) = session(ExamKind.LEVEL_FINAL)
    override suspend fun startPlacement() = session(ExamKind.PLACEMENT)

    override suspend fun submitLevelExam(attemptId: String, answers: Map<String, String>): ApiResult<ExamResult> {
        submitFailure?.let { submitFailure = null; return ApiResult.Failure(it) }
        submitted = answers
        val score = questions.count { answers[it.id] == "B" }
        val passed = score * 100 >= 80 * questions.size
        return ApiResult.Success(ExamResult(score, questions.size, passed, 80, if (passed) "EM-ABCD-EFGH-JKLM" else null, if (passed) null else 86_400_000L, false))
    }

    override suspend fun submitPlacement(attemptId: String, answers: Map<String, String>): ApiResult<PlacementResult> {
        submitted = answers
        return ApiResult.Success(PlacementResult(answers.size, questions.size, CefrLevel.A2, CefrLevel.B1))
    }

    override suspend fun certificates() =
        ApiResult.Success(listOf(Certificate(CefrLevel.A1, name, 18, 20, "EM-ABCD-EFGH-JKLM", 0)))
}
