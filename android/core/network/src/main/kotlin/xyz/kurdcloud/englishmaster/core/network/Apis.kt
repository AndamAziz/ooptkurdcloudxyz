package xyz.kurdcloud.englishmaster.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/** Supabase Auth (GoTrue) endpoints used by the app. */
interface AuthApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: PasswordGrantRequest): TokenResponseDto

    /** [redirectTo]: where the confirmation link sends the user (the app's auth-callback link). */
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: SignUpRequest, @Query("redirect_to") redirectTo: String? = null): SignUpResponseDto

    @POST("auth/v1/recover")
    suspend fun recover(@Body body: RecoverRequest, @Query("redirect_to") redirectTo: String? = null)

    /** Exchanges the code from a password-reset email for a session. */
    @POST("auth/v1/verify")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): TokenResponseDto

    /** The user an access token belongs to (used after an email link signs the user in). */
    @GET("auth/v1/user")
    suspend fun user(@Header("Authorization") bearer: String): AuthUserDto

    /** Sets a new password for the signed-in user (after a password-reset link). */
    @PUT("auth/v1/user")
    suspend fun updateUser(@Header("Authorization") bearer: String, @Body body: UpdateUserRequest): AuthUserDto

    @POST("auth/v1/logout")
    suspend fun logout(@Header("Authorization") bearer: String)

    /** Blocking variant used from OkHttp interceptors/authenticators. */
    @POST("auth/v1/token?grant_type=refresh_token")
    fun refresh(@Body body: RefreshGrantRequest): Call<TokenResponseDto>
}

/** PostgREST RPCs. Every call is authorised server-side; see backend/supabase/migrations. */
interface PracticeApi {
    /** v4 adds the Kurdish translations (v3 topic tags, v2 passages and media); older versions stay for older app builds. */
    @POST("rest/v1/rpc/get_practice_questions_v4")
    suspend fun getPracticeQuestions(@Body body: PracticeQuestionsRequest): List<PracticeQuestionDto>

    /** v2 also returns the Kurdish explanation. */
    @POST("rest/v1/rpc/submit_practice_response_v2")
    suspend fun submitPracticeResponse(@Body body: SubmitResponseRequest): List<SubmitResultDto>

    @POST("rest/v1/rpc/get_my_practice_summary")
    suspend fun getMyPracticeSummary(@Body body: Map<String, String> = emptyMap()): List<PracticeSummaryDto>

    /** Per level: published questions and the last change, to spot out-of-date packs. */
    @POST("rest/v1/rpc/get_question_pack_versions")
    suspend fun getQuestionPackVersions(@Body body: Map<String, String> = emptyMap()): List<PackVersionDto>
}
