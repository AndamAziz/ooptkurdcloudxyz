package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.model.EmailLink
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.AuthApi
import xyz.kurdcloud.englishmaster.core.network.PasswordGrantRequest
import xyz.kurdcloud.englishmaster.core.network.RecoverRequest
import xyz.kurdcloud.englishmaster.core.network.SignUpMetadata
import xyz.kurdcloud.englishmaster.core.network.SignUpRequest
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig
import xyz.kurdcloud.englishmaster.core.network.TokenResponseDto
import xyz.kurdcloud.englishmaster.core.network.UpdateUserRequest
import xyz.kurdcloud.englishmaster.core.network.authCall
import xyz.kurdcloud.englishmaster.core.network.toSession

enum class RegisterOutcome { SIGNED_IN, CONFIRM_EMAIL }

interface AuthRepository {
    val session: StateFlow<AuthSession?>
    val sessionExpired: SharedFlow<Unit>
    suspend fun signIn(email: String, password: String): ApiResult<Unit>
    suspend fun register(displayName: String, email: String, password: String): ApiResult<RegisterOutcome>
    suspend fun requestPasswordReset(email: String): ApiResult<Unit>
    suspend fun signOut()

    /** Signs in with the tokens from an email link (password reset or sign-up confirmation). */
    suspend fun completeEmailLink(link: EmailLink.SignedIn): ApiResult<Unit> = ApiResult.Failure(ApiError.NotConfigured)

    /** Sets a new password for the signed-in user. */
    suspend fun updatePassword(newPassword: String): ApiResult<Unit> = ApiResult.Failure(ApiError.NotConfigured)
}

class SupabaseAuthRepository(
    private val config: SupabaseConfig,
    private val api: AuthApi,
    private val store: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AuthRepository {
    override val session: StateFlow<AuthSession?> = store.session
    override val sessionExpired: SharedFlow<Unit> = store.sessionExpired

    override suspend fun signIn(email: String, password: String): ApiResult<Unit> = guarded {
        when (val result = authCall { api.signIn(PasswordGrantRequest(email.trim(), password)) }) {
            is ApiResult.Success -> {
                store.save(result.value.toSession(nowEpochSeconds()))
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun register(displayName: String, email: String, password: String): ApiResult<RegisterOutcome> = guarded {
        val request = SignUpRequest(email.trim(), password, SignUpMetadata(displayName.trim()))
        when (val result = authCall { api.signUp(request, EmailLink.REDIRECT) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val dto = result.value
                val user = dto.user
                val access = dto.accessToken
                val refresh = dto.refreshToken
                val expiresIn = dto.expiresIn
                if (access != null && refresh != null && expiresIn != null && user != null) {
                    store.save(TokenResponseDto(access, refresh, expiresIn, dto.expiresAt, user).toSession(nowEpochSeconds()))
                    ApiResult.Success(RegisterOutcome.SIGNED_IN)
                } else {
                    ApiResult.Success(RegisterOutcome.CONFIRM_EMAIL)
                }
            }
        }
    }

    override suspend fun requestPasswordReset(email: String): ApiResult<Unit> = guarded {
        authCall { api.recover(RecoverRequest(email.trim()), EmailLink.REDIRECT) }
    }

    override suspend fun completeEmailLink(link: EmailLink.SignedIn): ApiResult<Unit> = guarded {
        when (val user = authCall { api.user("Bearer ${link.accessToken}") }) {
            is ApiResult.Failure -> user
            is ApiResult.Success -> {
                val dto = TokenResponseDto(link.accessToken, link.refreshToken, link.expiresInSeconds, null, user.value)
                store.save(dto.toSession(nowEpochSeconds()))
                ApiResult.Success(Unit)
            }
        }
    }

    override suspend fun updatePassword(newPassword: String): ApiResult<Unit> = guarded {
        val current = store.current() ?: return@guarded ApiResult.Failure(ApiError.Unauthorized)
        authCall { api.updateUser("Bearer ${current.accessToken}", UpdateUserRequest(newPassword)) }.map { }
    }

    override suspend fun signOut() {
        val current = store.current()
        if (current != null && config.isConfigured) {
            // Best effort: revoke the refresh token server-side; sign out locally regardless.
            authCall { api.logout("Bearer ${current.accessToken}") }
        }
        store.remove()
    }

    private inline fun <T> guarded(block: () -> ApiResult<T>): ApiResult<T> =
        if (!config.isConfigured) ApiResult.Failure(ApiError.NotConfigured) else block()
}
