package xyz.kurdcloud.englishmaster.feature.admin

import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.model.ActivityEntry
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.ReviewState
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

class FakeStaffRepository : StaffRepository {
    var access = StaffAccess(canReviewQuestions = true, canManageUsers = true)
    val users = mutableListOf(
        StaffUser("me", "andam@example.com", "Andam", setOf(Role.STUDENT, Role.ADMIN), false, 2, 1),
        StaffUser("s1", "sara@example.com", "Sara", setOf(Role.STUDENT), false, 10, 7),
    )
    val questions = mutableListOf(
        StaffQuestion("q1", CefrLevel.A1, "I ___ a student.", listOf("is", "am", "are"), "B", "Use am with I.", "published", "unreviewed"),
        StaffQuestion("q2", CefrLevel.B1, "She suggested ___ to the beach.", listOf("going", "to go", "go"), "A", "Suggest + -ing.", "draft", "unreviewed"),
    )
    var failNext: ApiError? = null
    val calls = mutableListOf<String>()

    private fun <T> result(value: () -> T): ApiResult<T> =
        failNext?.let { failNext = null; ApiResult.Failure(it) } ?: ApiResult.Success(value())

    override suspend fun myAccess() = result { access }
    override suspend fun users(search: String?) = result {
        calls += "users:$search"
        users.filter { search.isNullOrBlank() || it.displayName.contains(search, true) || it.email.contains(search, true) }
    }
    override suspend fun setRole(userId: String, role: Role, granted: Boolean) = result {
        calls += "role:$userId:$role:$granted"
        val u = users.first { it.userId == userId }
        (if (granted) u.roles + role else u.roles - role)
    }
    override suspend fun setSuspended(userId: String, suspended: Boolean) = result { calls += "suspend:$userId:$suspended"; suspended }
    override suspend fun questions(level: CefrLevel) = result { questions.filter { it.level == level } }
    override suspend fun review(questionId: String, approved: Boolean, status: String?) = result {
        calls += "review:$questionId:$approved:$status"
        ReviewState(if (approved) "approved" else "unreviewed", status ?: questions.first { it.id == questionId }.status)
    }
    override suspend fun recentActivity() = result {
        listOf(ActivityEntry("2026-10-19T20:03:52Z", "admin.bootstrap_admin_granted", "Andam", null))
    }
}
