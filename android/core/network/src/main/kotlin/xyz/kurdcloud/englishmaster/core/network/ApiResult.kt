package xyz.kurdcloud.englishmaster.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Errors the UI and sync layer can act on. Raw server messages are never shown to users. */
sealed interface ApiError {
    /** No connection, DNS failure or timeout. Retryable. */
    data object Network : ApiError

    /** The session is missing or could not be refreshed. The user must sign in again. */
    data object Unauthorized : ApiError

    /** Too many requests. Retry later. */
    data object RateLimited : ApiError

    /** The account is suspended or the caller is not allowed to do this. */
    data object Forbidden : ApiError

    /** Input rejected by the server; retrying the same request will not help. */
    data class Validation(val code: String?) : ApiError

    /** Sign-in/sign-up specific failures. */
    data class Auth(val reason: AuthFailure) : ApiError

    /** Unexpected server response. Retryable. */
    data class Server(val httpStatus: Int) : ApiError

    /** The app was built without Supabase configuration. */
    data object NotConfigured : ApiError
}

enum class AuthFailure { INVALID_CREDENTIALS, EMAIL_NOT_CONFIRMED, USER_EXISTS, WEAK_PASSWORD, SIGNUP_DISABLED, OTHER }

val ApiError.isRetryable: Boolean
    get() = this is ApiError.Network || this is ApiError.RateLimited || this is ApiError.Server

internal val errorJson = Json { ignoreUnknownKeys = true }

/** Runs a Supabase Auth call and maps failures. */
suspend fun <T> authCall(block: suspend () -> T): ApiResult<T> = runCatchingApi(block) { status, body ->
    val dto = body?.let { runCatching { errorJson.decodeFromString(AuthErrorDto.serializer(), it) }.getOrNull() }
    val code = dto?.errorCode ?: dto?.error
    when {
        status == 429 || code?.startsWith("over_") == true -> ApiError.RateLimited
        code == "invalid_credentials" || code == "invalid_grant" -> ApiError.Auth(AuthFailure.INVALID_CREDENTIALS)
        code == "email_not_confirmed" -> ApiError.Auth(AuthFailure.EMAIL_NOT_CONFIRMED)
        code == "user_already_exists" || code == "email_exists" -> ApiError.Auth(AuthFailure.USER_EXISTS)
        code == "weak_password" -> ApiError.Auth(AuthFailure.WEAK_PASSWORD)
        code == "signup_disabled" -> ApiError.Auth(AuthFailure.SIGNUP_DISABLED)
        status >= 500 -> ApiError.Server(status)
        else -> ApiError.Auth(AuthFailure.OTHER)
    }
}

/** Runs a PostgREST call and maps failures using the Postgres SQLSTATE in the body. */
suspend fun <T> restCall(block: suspend () -> T): ApiResult<T> = runCatchingApi(block) { status, body ->
    val code = body?.let { runCatching { errorJson.decodeFromString(PostgrestErrorDto.serializer(), it) }.getOrNull() }?.code
    when {
        status == 401 || code == "28000" || code == "PGRST301" || code == "PGRST303" -> ApiError.Unauthorized
        code == "42501" -> ApiError.Forbidden
        status == 429 || code == "54000" -> ApiError.RateLimited
        code != null && (code.startsWith("22") || code.startsWith("23") || code == "PGRST202") -> ApiError.Validation(code)
        status >= 500 -> ApiError.Server(status)
        status == 403 -> ApiError.Forbidden
        else -> ApiError.Validation(code)
    }
}

private suspend fun <T> runCatchingApi(
    block: suspend () -> T,
    mapHttp: (status: Int, body: String?) -> ApiError,
): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    ApiResult.Failure(mapHttp(e.code(), body))
} catch (e: SessionExpiredException) {
    ApiResult.Failure(ApiError.Unauthorized)
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network)
} catch (e: SerializationException) {
    ApiResult.Failure(ApiError.Server(200))
}
