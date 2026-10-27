package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasswordGrantRequest(val email: String, val password: String)

@Serializable
data class RefreshGrantRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class SignUpRequest(val email: String, val password: String, val data: SignUpMetadata)

@Serializable
data class SignUpMetadata(@SerialName("display_name") val displayName: String)

@Serializable
data class RecoverRequest(val email: String)

/**
 * One-time code from a password-reset email. Browsers refuse to follow the reset link's redirect to
 * the app's own URL scheme, so the code in the same email is what the app actually uses.
 */
@Serializable
data class VerifyOtpRequest(val email: String, val token: String, val type: String)

@Serializable
data class UpdateUserRequest(val password: String)

@Serializable
data class AuthUserDto(val id: String, val email: String? = null)

/** Response of /auth/v1/token. */
@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: AuthUserDto,
)

/**
 * Response of /auth/v1/signup. With e-mail confirmation enabled Supabase returns the user
 * object without tokens; with auto-confirm it returns a session.
 */
@Serializable
data class SignUpResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: AuthUserDto? = null,
    val id: String? = null,
    val email: String? = null,
)

/** Supabase Auth error body (current and legacy shapes). */
@Serializable
data class AuthErrorDto(
    @SerialName("error_code") val errorCode: String? = null,
    val msg: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/** PostgREST error body. */
@Serializable
data class PostgrestErrorDto(val code: String? = null, val message: String? = null, val hint: String? = null)

@Serializable
data class PracticeQuestionsRequest(
    @SerialName("p_cefr_level") val level: String,
    @SerialName("p_after") val after: String? = null,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class PracticeQuestionDto(
    val id: String,
    @SerialName("cefr_level") val level: String,
    val skill: String,
    val prompt: String,
    val options: List<String>,
    val version: Int,
    @SerialName("review_status") val reviewStatus: String,
    @SerialName("updated_at") val updatedAt: String,
    val passage: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("prompt_ckb") val promptCkb: String? = null,
    @SerialName("options_ckb") val optionsCkb: List<String?>? = null,
    @SerialName("passage_ckb") val passageCkb: String? = null,
)

@Serializable
data class SubmitResponseRequest(
    @SerialName("p_response_id") val responseId: String,
    @SerialName("p_attempt_id") val attemptId: String,
    @SerialName("p_question_id") val questionId: String,
    @SerialName("p_chosen_option") val chosenOption: String,
    @SerialName("p_client_answered_at") val clientAnsweredAt: String? = null,
)

@Serializable
data class SubmitResultDto(
    @SerialName("response_id") val responseId: String,
    @SerialName("is_correct") val isCorrect: Boolean,
    @SerialName("correct_option") val correctOption: String,
    val explanation: String? = null,
    @SerialName("received_at") val receivedAt: String,
    val duplicate: Boolean,
    @SerialName("explanation_ckb") val explanationCkb: String? = null,
)

@Serializable
data class PracticeSummaryDto(
    @SerialName("cefr_level") val level: String,
    val answered: Long,
    val correct: Long,
)

@Serializable
data class PackVersionDto(
    @SerialName("cefr_level") val level: String,
    val published: Int,
    @SerialName("last_changed_at") val lastChangedAt: String? = null,
)

// --- Driving theory test -----------------------------------------------------------------------

@Serializable
data class TheorySectionsRequest(val unused: String? = null)

@Serializable
data class TheorySectionDto(
    val slug: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_ckb") val nameCkb: String? = null,
    val published: Int,
    val answered: Int,
    val correct: Int,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class TheoryQuestionsRequest(
    @SerialName("p_section_slug") val section: String,
    @SerialName("p_after") val after: String? = null,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class TheoryQuestionDto(
    val id: String,
    @SerialName("section_slug") val sectionSlug: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val prompt: String,
    @SerialName("prompt_ckb") val promptCkb: String? = null,
    val answer: String,
    @SerialName("answer_ckb") val answerCkb: String? = null,
    val explanation: String? = null,
    @SerialName("explanation_ckb") val explanationCkb: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    val version: Int,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SubmitTheoryResponseRequest(
    @SerialName("p_question_id") val questionId: String,
    @SerialName("p_is_correct") val isCorrect: Boolean,
    @SerialName("p_mode") val mode: String,
)

@Serializable
data class UpsertTheoryQuestionRequest(
    @SerialName("p_section_slug") val section: String,
    @SerialName("p_prompt") val prompt: String,
    @SerialName("p_answer") val answer: String,
    @SerialName("p_id") val id: String? = null,
    @SerialName("p_prompt_ckb") val promptCkb: String? = null,
    @SerialName("p_answer_ckb") val answerCkb: String? = null,
    @SerialName("p_explanation") val explanation: String? = null,
    @SerialName("p_explanation_ckb") val explanationCkb: String? = null,
    @SerialName("p_media_path") val mediaPath: String? = null,
    @SerialName("p_publish") val publish: Boolean,
)
