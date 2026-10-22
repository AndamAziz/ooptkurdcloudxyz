package xyz.kurdcloud.englishmaster.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Learner progress, exams and certificates (see backend migration exams_and_certificates).
 * Questions are chosen and scored by the server; answer keys never reach the device.
 */
interface LearningApi {
    /** v2 adds certificate_status (certificates need a teacher's approval). */
    @POST("rest/v1/rpc/get_learning_overview_v2")
    suspend fun overview(@Body body: Map<String, String> = emptyMap()): List<LevelOverviewDto>

    @POST("rest/v1/rpc/get_learner_profile")
    suspend fun profile(@Body body: Map<String, String> = emptyMap()): List<LearnerProfileDto>

    @POST("rest/v1/rpc/update_my_profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ProfileDto

    @POST("rest/v1/rpc/start_level_exam")
    suspend fun startLevelExam(@Body body: StartLevelExamRequest): List<ExamStartDto>

    @POST("rest/v1/rpc/submit_level_exam")
    suspend fun submitLevelExam(@Body body: SubmitExamRequest): List<ExamResultDto>

    @POST("rest/v1/rpc/start_placement_test")
    suspend fun startPlacement(@Body body: Map<String, String> = emptyMap()): List<ExamStartDto>

    @POST("rest/v1/rpc/submit_placement_test")
    suspend fun submitPlacement(@Body body: SubmitExamRequest): List<PlacementResultDto>

    @POST("rest/v1/rpc/get_checkpoints")
    suspend fun checkpoints(@Body body: Map<String, String> = emptyMap()): List<CheckpointDto>

    @POST("rest/v1/rpc/start_checkpoint")
    suspend fun startCheckpoint(@Body body: StartCheckpointRequest): List<ExamStartDto>

    @POST("rest/v1/rpc/submit_checkpoint")
    suspend fun submitCheckpoint(@Body body: SubmitExamRequest): List<ExamResultDto>

    /** Placement stage 2. */
    @POST("rest/v1/rpc/start_placement_confirmation")
    suspend fun startPlacementConfirmation(@Body body: Map<String, String> = emptyMap()): List<ExamStartDto>

    @POST("rest/v1/rpc/submit_placement_confirmation")
    suspend fun submitPlacementConfirmation(@Body body: SubmitExamRequest): List<ConfirmationResultDto>

    /** Placement tests left in the rolling 24 hours and when the next one opens. */
    @POST("rest/v1/rpc/get_placement_status_v2")
    suspend fun placementStatus(@Body body: Map<String, String> = emptyMap()): List<PlacementStatusDto>

    /** Deletes the signed-in learner's account and all their data (Google Play requirement). */
    @POST("rest/v1/rpc/delete_my_account")
    suspend fun deleteMyAccount(@Body body: Map<String, String> = emptyMap())

    @POST("rest/v1/rpc/get_my_certificates")
    suspend fun certificates(@Body body: Map<String, String> = emptyMap()): List<CertificateDto>
}

@Serializable
data class LevelOverviewDto(
    @SerialName("cefr_level") val level: String,
    val published: Int,
    val answered: Int,
    val correct: Int,
    val required: Int,
    @SerialName("required_percent") val requiredPercent: Int,
    @SerialName("exam_unlocked") val examUnlocked: Boolean,
    @SerialName("exam_available_at") val examAvailableAt: String? = null,
    @SerialName("exam_in_progress") val examInProgress: String? = null,
    @SerialName("last_exam_score") val lastExamScore: Int? = null,
    @SerialName("last_exam_total") val lastExamTotal: Int? = null,
    @SerialName("certificate_code") val certificateCode: String? = null,
    @SerialName("certificate_issued_at") val certificateIssuedAt: String? = null,
    @SerialName("certificate_status") val certificateStatus: String? = null,
)

@Serializable
data class LearnerProfileDto(
    @SerialName("display_name") val displayName: String,
    @SerialName("placement_level") val placementLevel: String? = null,
    @SerialName("total_answered") val totalAnswered: Int,
    @SerialName("streak_days") val streakDays: Int,
)

@Serializable
data class UpdateProfileRequest(@SerialName("p_display_name") val displayName: String)

@Serializable
data class ProfileDto(@SerialName("display_name") val displayName: String)

@Serializable
data class StartLevelExamRequest(@SerialName("p_cefr_level") val level: String)

@Serializable
data class ExamQuestionDto(
    val id: String,
    @SerialName("cefr_level") val level: String,
    val prompt: String,
    val options: List<String>,
    val passage: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
)

@Serializable
data class ExamStartDto(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("cefr_level") val level: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    val questions: List<ExamQuestionDto>,
)

/** [answers] maps question id to the chosen option letter. */
@Serializable
data class SubmitExamRequest(
    @SerialName("p_attempt_id") val attemptId: String,
    @SerialName("p_answers") val answers: Map<String, String>,
)

@Serializable
data class ExamResultDto(
    val score: Int,
    val total: Int,
    val passed: Boolean,
    @SerialName("pass_percent") val passPercent: Int,
    @SerialName("certificate_code") val certificateCode: String? = null,
    @SerialName("next_attempt_at") val nextAttemptAt: String? = null,
    val expired: Boolean,
)

@Serializable
data class PlacementResultDto(
    val score: Int,
    val total: Int,
    @SerialName("passed_level") val passedLevel: String? = null,
    @SerialName("recommended_level") val recommendedLevel: String,
)

@Serializable
data class CertificateDto(
    @SerialName("cefr_level") val level: String,
    @SerialName("holder_name") val holderName: String,
    val score: Int,
    val total: Int,
    @SerialName("verification_code") val code: String,
    @SerialName("issued_at") val issuedAt: String,
)

@Serializable
data class PlacementStatusDto(
    @SerialName("attempts_left") val attemptsLeft: Int,
    @SerialName("next_attempt_at") val nextAttemptAt: String? = null,
    @SerialName("in_progress") val inProgress: Boolean = false,
    @SerialName("confirm_level") val confirmLevel: String? = null,
    @SerialName("confirm_in_progress") val confirmInProgress: Boolean = false,
    val confirmed: Boolean = false,
)

@Serializable
data class CheckpointDto(
    @SerialName("cefr_level") val level: String,
    val checkpoint: Int,
    val required: Int,
    val unlocked: Boolean,
    val passed: Boolean,
    @SerialName("last_score") val lastScore: Int? = null,
    @SerialName("last_total") val lastTotal: Int? = null,
    @SerialName("available_at") val availableAt: String? = null,
    @SerialName("in_progress") val inProgress: String? = null,
)

@Serializable
data class StartCheckpointRequest(
    @SerialName("p_cefr_level") val level: String,
    @SerialName("p_checkpoint") val checkpoint: Int,
)

@Serializable
data class ConfirmationResultDto(
    val score: Int,
    val total: Int,
    val confirmed: Boolean,
    @SerialName("tested_level") val testedLevel: String,
    @SerialName("recommended_level") val recommendedLevel: String,
)
