package xyz.kurdcloud.englishmaster.core.model

/** Matches `public.app_role`. The server is the authority on what each role may do. */
enum class Role(val apiValue: String) {
    STUDENT("student"), TEACHER("teacher"), ADMIN("admin");

    companion object {
        fun fromApi(value: String): Role? = entries.firstOrNull { it.apiValue == value }
    }
}

/** What the signed-in user may open in the staff section (UI convenience only). */
data class StaffAccess(val canReviewQuestions: Boolean, val canManageUsers: Boolean) {
    val any: Boolean get() = canReviewQuestions || canManageUsers

    /** Teachers and admins can see learners and correct their names; roles/suspension stay admin-only. */
    val canSeeUsers: Boolean get() = any

    companion object {
        val NONE = StaffAccess(canReviewQuestions = false, canManageUsers = false)

        fun from(roles: Set<Role>) = StaffAccess(
            canReviewQuestions = Role.ADMIN in roles || Role.TEACHER in roles,
            canManageUsers = Role.ADMIN in roles,
        )
    }
}

data class StaffUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val roles: Set<Role>,
    val suspended: Boolean,
    val answered: Long,
    val correct: Long,
)

data class StaffQuestion(
    val id: String,
    val level: CefrLevel,
    val prompt: String,
    val options: List<String>,
    val correctOption: String,
    val explanation: String?,
    val status: String,
    val reviewStatus: String,
    val passage: String? = null,
    val media: QuestionMedia? = null,
)

/** A question written by a teacher in the app. [correctIndex] points into [options]. */
data class NewQuestion(
    val level: CefrLevel,
    val skill: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String?,
    val passage: String?,
    val mediaType: MediaType?,
    /** Object path inside the question-media bucket, after upload. */
    val mediaPath: String?,
    val mediaCaption: String?,
    val publish: Boolean,
)

data class ActivityEntry(
    val occurredAt: String,
    val eventType: String,
    val actorName: String?,
    val detail: String?,
)

/** A question's review state as stored by the server after a review action. */
data class ReviewState(val reviewStatus: String, val status: String)
