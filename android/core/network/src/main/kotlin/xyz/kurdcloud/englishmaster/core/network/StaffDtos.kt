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
    val skill: String = "grammar",
    val prompt: String,
    val options: List<String>,
    @SerialName("correct_option") val correctOption: String,
    val explanation: String? = null,
    val status: String,
    @SerialName("review_status") val reviewStatus: String,
    val passage: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
)

@Serializable
data class CreateQuestionRequest(
    @SerialName("p_cefr_level") val level: String,
    @SerialName("p_skill") val skill: String,
    @SerialName("p_prompt") val prompt: String,
    @SerialName("p_options") val options: List<String>,
    @SerialName("p_correct_option") val correctOption: String,
    @SerialName("p_explanation") val explanation: String? = null,
    @SerialName("p_passage") val passage: String? = null,
    @SerialName("p_media_type") val mediaType: String? = null,
    @SerialName("p_media_path") val mediaPath: String? = null,
    @SerialName("p_media_caption") val mediaCaption: String? = null,
    @SerialName("p_publish") val publish: Boolean = true,
)

@Serializable
data class PublishDraftsRequest(@SerialName("p_cefr_level") val level: String)

@Serializable
data class SetDisplayNameRequest(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_display_name") val displayName: String,
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

@Serializable
data class ListCertificatesRequest(@SerialName("p_status") val status: String, @SerialName("p_limit") val limit: Int = 100)

@Serializable
data class CertificateRequestDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("cefr_level") val level: String,
    val score: Int,
    val total: Int,
    @SerialName("requested_at") val requestedAt: String,
    val status: String,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewer_name") val reviewerName: String? = null,
)

@Serializable
data class ReviewCertificateRequest(
    @SerialName("p_certificate_id") val certificateId: String,
    @SerialName("p_approve") val approve: Boolean,
    @SerialName("p_note") val note: String? = null,
)

@Serializable
data class CertificateReviewDto(val status: String, @SerialName("verification_code") val verificationCode: String? = null)

@Serializable
data class UpdateQuestionRequest(
    @SerialName("p_question_id") val questionId: String,
    @SerialName("p_cefr_level") val level: String,
    @SerialName("p_skill") val skill: String,
    @SerialName("p_prompt") val prompt: String,
    @SerialName("p_options") val options: List<String>,
    @SerialName("p_correct_option") val correctOption: String,
    @SerialName("p_explanation") val explanation: String? = null,
    @SerialName("p_passage") val passage: String? = null,
    @SerialName("p_media_type") val mediaType: String? = null,
    @SerialName("p_media_path") val mediaPath: String? = null,
    @SerialName("p_media_caption") val mediaCaption: String? = null,
)

@Serializable
data class DeleteQuestionRequest(@SerialName("p_question_id") val questionId: String)
