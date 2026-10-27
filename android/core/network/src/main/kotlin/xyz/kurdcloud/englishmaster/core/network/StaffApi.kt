package xyz.kurdcloud.englishmaster.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff RPCs (see backend migration staff_tools). Each call is authorised by the server;
 * a student calling them gets 42501 (mapped to [ApiError.Forbidden]).
 */
interface StaffApi {
    @POST("rest/v1/rpc/get_my_roles")
    suspend fun getMyRoles(@Body body: Map<String, String> = emptyMap()): List<String>

    @POST("rest/v1/rpc/admin_list_users")
    suspend fun listUsers(@Body body: ListUsersRequest): List<StaffUserDto>

    @POST("rest/v1/rpc/admin_set_role")
    suspend fun setRole(@Body body: SetRoleRequest): List<String>

    @POST("rest/v1/rpc/admin_set_account_status")
    suspend fun setAccountStatus(@Body body: SetAccountStatusRequest): String

    /** Readable only by teachers/admins (RLS policy "questions: staff read"). */
    @GET("rest/v1/assessment_questions")
    suspend fun questions(
        @Query("cefr_level") levelFilter: String,
        @Query("select") select: String =
            "id,cefr_level,skill,prompt,options,correct_option,explanation,status,review_status,passage,media_type,media_path,media_caption",
        /** Questions an admin deleted (kept only for answer history) are not listed. */
        @Query("deleted_at") deletedFilter: String = "is.null",
        @Query("order") order: String = "review_status.desc,prompt.asc",
    ): List<StaffQuestionDto>

    @POST("rest/v1/rpc/staff_review_question")
    suspend fun reviewQuestion(@Body body: ReviewQuestionRequest): List<ReviewResultDto>

    @POST("rest/v1/rpc/staff_create_question")
    suspend fun createQuestion(@Body body: CreateQuestionRequest): String

    @POST("rest/v1/rpc/staff_update_question")
    suspend fun updateQuestion(@Body body: UpdateQuestionRequest): Int

    /** Admin only; returns "deleted" or "archived". */
    @POST("rest/v1/rpc/staff_delete_question")
    suspend fun deleteQuestion(@Body body: DeleteQuestionRequest): String

    @POST("rest/v1/rpc/staff_publish_drafts")
    suspend fun publishDrafts(@Body body: PublishDraftsRequest): Int

    @POST("rest/v1/rpc/staff_set_display_name")
    suspend fun setDisplayName(@Body body: SetDisplayNameRequest): String

    /** Uploads a picture/video to the public question-media bucket (teachers/admins only, by policy). */
    @POST("storage/v1/object/question-media/{path}")
    suspend fun uploadMedia(
        @Path("path", encoded = true) path: String,
        @Body body: okhttp3.RequestBody,
        @Header("x-upsert") upsert: String = "false",
    ): okhttp3.ResponseBody

    @POST("rest/v1/rpc/staff_list_certificates")
    suspend fun certificateRequests(@Body body: ListCertificatesRequest): List<CertificateRequestDto>

    @POST("rest/v1/rpc/staff_review_certificate")
    suspend fun reviewCertificate(@Body body: ReviewCertificateRequest): List<CertificateReviewDto>

    @POST("rest/v1/rpc/admin_recent_activity")
    suspend fun recentActivity(@Body body: RecentActivityRequest): List<ActivityDto>

    // --- Driving theory test ---

    /** Adds or edits a theory question; returns its id. */
    @POST("rest/v1/rpc/staff_upsert_theory_question")
    suspend fun upsertTheoryQuestion(@Body body: UpsertTheoryQuestionRequest): String

    /** Publishes every draft question of one chapter; returns how many. */
    @POST("rest/v1/rpc/staff_publish_theory_drafts")
    suspend fun publishTheoryDrafts(@Body body: Map<String, String>): Int
}
