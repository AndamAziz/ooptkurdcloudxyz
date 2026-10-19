package xyz.kurdcloud.englishmaster.core.network

import xyz.kurdcloud.englishmaster.core.model.AuthSession
import java.io.IOException

/** Serialises refresh-token exchanges so concurrent requests refresh only once. */
class TokenRefresher(
    private val sessions: SessionProvider,
    private val authApi: AuthApi,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val lock = Any()

    /**
     * Returns a session whose access token differs from [staleAccessToken], refreshing if
     * needed. Returns null (and clears the session) when the refresh token is rejected.
     * Throws [IOException] on transient failures.
     */
    fun refresh(staleAccessToken: String?): AuthSession? = synchronized(lock) {
        val current = sessions.current() ?: return null
        if (staleAccessToken != null && current.accessToken != staleAccessToken) return current

        val response = authApi.refresh(RefreshGrantRequest(current.refreshToken)).execute()
        if (response.isSuccessful) {
            val body = response.body() ?: throw IOException("Empty refresh response")
            return body.toSession(nowEpochSeconds()).also(sessions::update)
        }
        if (response.code() in 400..499 && response.code() != 429) {
            sessions.clear(expired = true)
            return null
        }
        throw IOException("Token refresh failed with HTTP ${response.code()}")
    }
}

fun TokenResponseDto.toSession(nowEpochSeconds: Long): AuthSession = AuthSession(
    userId = user.id,
    email = user.email,
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAtEpochSeconds = expiresAt ?: (nowEpochSeconds + expiresIn),
)
