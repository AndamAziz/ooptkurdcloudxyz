package xyz.kurdcloud.englishmaster.core.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import xyz.kurdcloud.englishmaster.core.model.ActivityEntry
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ReviewState
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
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
}

class SupabaseStaffRepository(private val api: StaffApi) : StaffRepository {

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
                StaffQuestion(it.id, CefrLevel.fromApi(it.level), it.prompt, it.options, it.correctOption, it.explanation, it.status, it.reviewStatus)
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
}

internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
