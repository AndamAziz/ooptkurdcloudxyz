package xyz.kurdcloud.englishmaster.core.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.kurdcloud.englishmaster.core.model.ActivityEntry
import xyz.kurdcloud.englishmaster.core.model.CertificateRequest
import xyz.kurdcloud.englishmaster.core.model.CertificateRequestStatus
import xyz.kurdcloud.englishmaster.core.model.CredentialRules
import xyz.kurdcloud.englishmaster.core.model.DeleteOutcome
import xyz.kurdcloud.englishmaster.core.model.NewQuestion
import xyz.kurdcloud.englishmaster.core.model.NewTheoryQuestion
import xyz.kurdcloud.englishmaster.core.model.optionLetter
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ReviewState
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.CreateQuestionRequest
import xyz.kurdcloud.englishmaster.core.network.DeleteQuestionRequest
import xyz.kurdcloud.englishmaster.core.network.UpdateQuestionRequest
import xyz.kurdcloud.englishmaster.core.network.UpsertTheoryQuestionRequest
import xyz.kurdcloud.englishmaster.core.network.ListCertificatesRequest
import xyz.kurdcloud.englishmaster.core.network.ReviewCertificateRequest
import xyz.kurdcloud.englishmaster.core.network.PublishDraftsRequest
import xyz.kurdcloud.englishmaster.core.network.SetDisplayNameRequest
import xyz.kurdcloud.englishmaster.core.network.ListUsersRequest
import xyz.kurdcloud.englishmaster.core.network.RecentActivityRequest
import xyz.kurdcloud.englishmaster.core.network.ReviewQuestionRequest
import xyz.kurdcloud.englishmaster.core.network.SetAccountStatusRequest
import xyz.kurdcloud.englishmaster.core.network.SetRoleRequest
import xyz.kurdcloud.englishmaster.core.network.StaffApi
import xyz.kurdcloud.englishmaster.core.network.restCall

/** Online-only staff operations; every one is authorised again by the server. */
interface StaffRepository {
    suspend fun myAccess(): ApiResult<StaffAccess>
    suspend fun users(search: String?): ApiResult<List<StaffUser>>
    suspend fun setRole(userId: String, role: Role, granted: Boolean): ApiResult<Set<Role>>
    suspend fun setSuspended(userId: String, suspended: Boolean): ApiResult<Boolean>
    suspend fun questions(level: CefrLevel): ApiResult<List<StaffQuestion>>
    /** Sets the review flag; [status] ("published"/"retired") changes availability to students when given. */
    suspend fun review(questionId: String, approved: Boolean, status: String? = null): ApiResult<ReviewState>
    suspend fun recentActivity(): ApiResult<List<ActivityEntry>>

    /** Corrects a learner's name (teachers and admins). Certificates already issued keep their name. */
    suspend fun setDisplayName(userId: String, name: String): ApiResult<String> = ApiResult.Failure(ApiError.Forbidden)
    /** Uploads a picture or video; returns its object path in the question-media bucket. */
    suspend fun uploadMedia(bytes: ByteArray, mimeType: String): ApiResult<String> = ApiResult.Failure(ApiError.Forbidden)
    suspend fun createQuestion(question: NewQuestion): ApiResult<String> = ApiResult.Failure(ApiError.Forbidden)
    /** Approves and publishes every unreviewed draft of [level]; returns how many. */
    suspend fun publishDrafts(level: CefrLevel): ApiResult<Int> = ApiResult.Failure(ApiError.Forbidden)

    /** Saves an edited question (teachers and admins); returns its new version. [question].publish is ignored. */
    suspend fun updateQuestion(id: String, question: NewQuestion): ApiResult<Int> = ApiResult.Failure(ApiError.Forbidden)

    /** Admins only. Unanswered questions are deleted; answered ones are archived. */
    suspend fun deleteQuestion(id: String): ApiResult<DeleteOutcome> = ApiResult.Failure(ApiError.Forbidden)

    /** Certificate requests with [status] (oldest pending first). Teachers and admins only. */
    suspend fun certificateRequests(status: CertificateRequestStatus): ApiResult<List<CertificateRequest>> =
        ApiResult.Failure(ApiError.Forbidden)

    /** Adds a driving-theory question (teachers and admins); returns its id. */
    suspend fun addTheoryQuestion(question: NewTheoryQuestion): ApiResult<String> = ApiResult.Failure(ApiError.Forbidden)

    /** Publishes every draft question of one theory chapter; returns how many. */
    suspend fun publishTheoryDrafts(sectionSlug: String): ApiResult<Int> = ApiResult.Failure(ApiError.Forbidden)

    /** Approves or rejects a pending certificate; returns the new status. */
    suspend fun reviewCertificate(id: String, approve: Boolean, note: String? = null): ApiResult<CertificateRequestStatus> =
        ApiResult.Failure(ApiError.Forbidden)
}

class SupabaseStaffRepository(
    private val api: StaffApi,
    private val media: MediaFiles? = null,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : StaffRepository {

    override suspend fun myAccess(): ApiResult<StaffAccess> =
        restCall { api.getMyRoles() }.map { roles -> StaffAccess.from(roles.mapNotNull(Role::fromApi).toSet()) }

    override suspend fun users(search: String?): ApiResult<List<StaffUser>> =
        restCall { api.listUsers(ListUsersRequest(search?.trim()?.takeIf { it.isNotEmpty() })) }.map { rows ->
            rows.map {
                StaffUser(
                    userId = it.userId,
                    email = it.email.orEmpty(),
                    displayName = it.displayName,
                    roles = it.roles.mapNotNull(Role::fromApi).toSet(),
                    suspended = it.accountStatus == "suspended",
                    answered = it.answered,
                    correct = it.correct,
                )
            }
        }

    override suspend fun setRole(userId: String, role: Role, granted: Boolean): ApiResult<Set<Role>> {
        if (role == Role.STUDENT) return ApiResult.Failure(ApiError.Validation("22023"))
        return restCall { api.setRole(SetRoleRequest(userId, role.apiValue, granted)) }
            .map { roles -> roles.mapNotNull(Role::fromApi).toSet() }
    }

    override suspend fun setSuspended(userId: String, suspended: Boolean): ApiResult<Boolean> =
        restCall { api.setAccountStatus(SetAccountStatusRequest(userId, if (suspended) "suspended" else "active")) }
            .map { it == "suspended" }

    override suspend fun questions(level: CefrLevel): ApiResult<List<StaffQuestion>> =
        restCall { api.questions("eq.${level.apiValue}") }.map { rows ->
            rows.map {
                StaffQuestion(
                    it.id, CefrLevel.fromApi(it.level), it.prompt, it.options, it.correctOption, it.explanation, it.status, it.reviewStatus,
                    it.passage, questionMedia(it.mediaType, it.mediaPath, it.mediaCaption, null, media),
                    skill = it.skill, mediaPath = it.mediaPath,
                )
            }
        }

    override suspend fun review(questionId: String, approved: Boolean, status: String?): ApiResult<ReviewState> {
        val request = ReviewQuestionRequest(questionId, if (approved) "approved" else "unreviewed", status)
        return restCall { api.reviewQuestion(request) }.map { rows -> rows.single().let { ReviewState(it.reviewStatus, it.status) } }
    }

    override suspend fun recentActivity(): ApiResult<List<ActivityEntry>> =
        restCall { api.recentActivity(RecentActivityRequest(100)) }.map { rows ->
            rows.map { row ->
                val detail = row.metadata?.entries
                    ?.filterNot { it.key == "attempt_id" }
                    ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { "$k: $it" } }
                    ?.joinToString(", ")
                    ?.takeIf { it.isNotEmpty() }
                ActivityEntry(row.occurredAt, row.eventType, row.actorName, detail)
            }
        }

    override suspend fun setDisplayName(userId: String, name: String): ApiResult<String> {
        if (!CredentialRules.isValidDisplayName(name)) return ApiResult.Failure(ApiError.Validation("22023"))
        return restCall { api.setDisplayName(SetDisplayNameRequest(userId, name.trim())) }
    }

    override suspend fun uploadMedia(bytes: ByteArray, mimeType: String): ApiResult<String> {
        val ext = when (mimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            else -> return ApiResult.Failure(ApiError.Validation("unsupported_media"))
        }
        val limit = if (ext == "mp4") MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
        if (bytes.size > limit) return ApiResult.Failure(ApiError.Validation("media_too_large"))
        val path = "staff/${newId()}.$ext"
        return restCall { api.uploadMedia(path, bytes.toRequestBody(mimeType.toMediaType())).close() }.map { path }
    }

    override suspend fun createQuestion(question: NewQuestion): ApiResult<String> = restCall {
        api.createQuestion(
            CreateQuestionRequest(
                level = question.level.apiValue,
                skill = question.skill,
                prompt = question.prompt.trim(),
                options = question.options.map { it.trim() },
                correctOption = optionLetter(question.correctIndex),
                explanation = question.explanation?.trim()?.takeIf { it.isNotEmpty() },
                passage = question.passage?.trim()?.takeIf { it.isNotEmpty() },
                mediaType = question.mediaType?.apiValue,
                mediaPath = question.mediaPath,
                mediaCaption = question.mediaCaption?.trim()?.takeIf { it.isNotEmpty() },
                publish = question.publish,
            ),
        )
    }

    override suspend fun updateQuestion(id: String, question: NewQuestion): ApiResult<Int> = restCall {
        api.updateQuestion(
            UpdateQuestionRequest(
                questionId = id,
                level = question.level.apiValue,
                skill = question.skill,
                prompt = question.prompt.trim(),
                options = question.options.map { it.trim() },
                correctOption = optionLetter(question.correctIndex),
                explanation = question.explanation?.trim()?.takeIf { it.isNotEmpty() },
                passage = question.passage?.trim()?.takeIf { it.isNotEmpty() },
                mediaType = question.mediaType?.apiValue,
                mediaPath = question.mediaPath,
                mediaCaption = question.mediaCaption?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    override suspend fun deleteQuestion(id: String): ApiResult<DeleteOutcome> =
        restCall { api.deleteQuestion(DeleteQuestionRequest(id)) }
            .map { if (it == "archived") DeleteOutcome.ARCHIVED else DeleteOutcome.DELETED }

    override suspend fun publishDrafts(level: CefrLevel): ApiResult<Int> =
        restCall { api.publishDrafts(PublishDraftsRequest(level.apiValue)) }

    override suspend fun certificateRequests(status: CertificateRequestStatus): ApiResult<List<CertificateRequest>> =
        restCall { api.certificateRequests(ListCertificatesRequest(status.apiValue)) }.map { rows ->
            rows.map {
                CertificateRequest(
                    id = it.id,
                    userId = it.userId,
                    email = it.email,
                    displayName = it.displayName.orEmpty(),
                    level = CefrLevel.fromApi(it.level),
                    score = it.score,
                    total = it.total,
                    requestedAt = it.requestedAt,
                    status = CertificateRequestStatus.fromApi(it.status),
                    reviewedAt = it.reviewedAt,
                    reviewerName = it.reviewerName,
                )
            }
        }

    override suspend fun addTheoryQuestion(question: NewTheoryQuestion): ApiResult<String> {
        if (!question.isValid) return ApiResult.Failure(ApiError.Validation("22023"))
        return restCall {
            api.upsertTheoryQuestion(
                UpsertTheoryQuestionRequest(
                    section = question.sectionSlug,
                    prompt = question.prompt.trim(),
                    answer = question.answer.trim(),
                    promptCkb = question.promptKu.cleaned(),
                    answerCkb = question.answerKu.cleaned(),
                    explanation = question.explanation.cleaned(),
                    explanationCkb = question.explanationKu.cleaned(),
                    mediaPath = question.mediaPath,
                    publish = question.publish,
                ),
            )
        }
    }

    override suspend fun publishTheoryDrafts(sectionSlug: String): ApiResult<Int> =
        restCall { api.publishTheoryDrafts(mapOf("p_section_slug" to sectionSlug)) }

    override suspend fun reviewCertificate(id: String, approve: Boolean, note: String?): ApiResult<CertificateRequestStatus> =
        restCall { api.reviewCertificate(ReviewCertificateRequest(id, approve, note?.trim()?.takeIf { it.isNotEmpty() })) }
            .map { rows -> CertificateRequestStatus.fromApi(rows.single().status) }

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        const val MAX_VIDEO_BYTES = 25 * 1024 * 1024
    }
}

/** An optional field the staff form left blank is sent as absent, not as an empty string. */
private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
