package xyz.kurdcloud.englishmaster.core.network

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Adds the public API key required by every Supabase endpoint. */
class ApiKeyInterceptor(private val anonKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("apikey", anonKey).build())
}

/** Attaches the user's access token, refreshing it shortly before it expires. */
class BearerTokenInterceptor(
    private val sessions: SessionProvider,
    private val refresher: TokenRefresher,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var session = sessions.current()
        if (session != null && session.expiresWithin(REFRESH_MARGIN_SECONDS, nowEpochSeconds())) {
            session = refresher.refresh(session.accessToken) ?: throw SessionExpiredException()
        }
        val request = chain.request().newBuilder()
        if (session != null) request.header("Authorization", "Bearer ${session.accessToken}")
        return chain.proceed(request.build())
    }

    private companion object {
        const val REFRESH_MARGIN_SECONDS = 60L
    }
}

/** On 401, refreshes the token once and retries the request. */
class RefreshingAuthenticator(private val refresher: TokenRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val sent = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val session = refresher.refresh(sent) ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer ${session.accessToken}").build()
    }
}
