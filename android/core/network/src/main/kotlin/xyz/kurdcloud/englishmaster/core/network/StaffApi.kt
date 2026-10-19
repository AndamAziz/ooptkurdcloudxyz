package xyz.kurdcloud.englishmaster.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
        @Query("select") select: String = "id,cefr_level,prompt,options,correct_option,explanation,status,review_status",
        @Query("order") order: String = "review_status.desc,prompt.asc",
    ): List<StaffQuestionDto>

    @POST("rest/v1/rpc/staff_review_question")
    suspend fun reviewQuestion(@Body body: ReviewQuestionRequest): List<ReviewResultDto>

    @POST("rest/v1/rpc/admin_recent_activity")
    suspend fun recentActivity(@Body body: RecentActivityRequest): List<ActivityDto>
}
