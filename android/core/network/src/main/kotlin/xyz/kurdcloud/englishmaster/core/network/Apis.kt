package xyz.kurdcloud.englishmaster.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** Supabase Auth (GoTrue) endpoints used by the app. */
interface AuthApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: PasswordGrantRequest): TokenResponseDto

    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: SignUpRequest): SignUpResponseDto

    @POST("auth/v1/recover")
    suspend fun recover(@Body body: RecoverRequest)

    @POST("auth/v1/logout")
    suspend fun logout(@Header("Authorization") bearer: String)

    /** Blocking variant used from OkHttp interceptors/authenticators. */
    @POST("auth/v1/token?grant_type=refresh_token")
    fun refresh(@Body body: RefreshGrantRequest): Call<TokenResponseDto>
}

/** PostgREST RPCs. Every call is authorised server-side; see backend/supabase/migrations. */
interface PracticeApi {
    /** v2 adds passages and media; v1 stays on the server for older app builds. */
    @POST("rest/v1/rpc/get_practice_questions_v2")
    suspend fun getPracticeQuestions(@Body body: PracticeQuestionsRequest): List<PracticeQuestionDto>

    @POST("rest/v1/rpc/submit_practice_response")
    suspend fun submitPracticeResponse(@Body body: SubmitResponseRequest): List<SubmitResultDto>

    @POST("rest/v1/rpc/get_my_practice_summary")
    suspend fun getMyPracticeSummary(@Body body: Map<String, String> = emptyMap()): List<PracticeSummaryDto>
}
