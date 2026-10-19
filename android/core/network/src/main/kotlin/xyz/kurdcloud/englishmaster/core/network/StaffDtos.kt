package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListUsersRequest(
    @SerialName("p_search") val search: String? = null,
    @SerialName("p_limit") val limit: Int = 100,
    @SerialName("p_offset") val offset: Int = 0,
)

@Serializable
data class StaffUserDto(
    @SerialName("user_id") val userId: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String,
    val roles: List<String>,
    @SerialName("account_status") val accountStatus: String,
    val answered: Long,
    val correct: Long,
)

@Serializable
data class SetRoleRequest(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_role") val role: String,
    @SerialName("p_granted") val granted: Boolean,
)

@Serializable
data class SetAccountStatusRequest(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_status") val status: String,
)

@Serializable
data class StaffQuestionDto(
    val id: String,
    @SerialName("cefr_level") val level: String,
    val prompt: String,
    val options: List<String>,
    @SerialName("correct_option") val correctOption: String,
    val explanation: String? = null,
    val status: String,
    @SerialName("review_status") val reviewStatus: String,
)

@Serializable
data class ReviewQuestionRequest(
    @SerialName("p_question_id") val questionId: String,
    @SerialName("p_review_status") val reviewStatus: String,
    @SerialName("p_status") val status: String? = null,
)

@Serializable
data class ReviewResultDto(val id: String, @SerialName("review_status") val reviewStatus: String, val status: String)

@Serializable
data class RecentActivityRequest(@SerialName("p_limit") val limit: Int = 100)

@Serializable
data class ActivityDto(
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("entity_type") val entityType: String? = null,
    val metadata: kotlinx.serialization.json.JsonObject? = null,
)
