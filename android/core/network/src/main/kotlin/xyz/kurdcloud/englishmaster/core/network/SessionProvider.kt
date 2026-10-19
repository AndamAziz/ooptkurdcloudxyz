package xyz.kurdcloud.englishmaster.core.network

import xyz.kurdcloud.englishmaster.core.model.AuthSession
import java.io.IOException

/**
 * Access to the signed-in session for the HTTP layer. Implementations may block (they are
 * called from OkHttp threads), so they must not be called on the main thread.
 */
interface SessionProvider {
    fun current(): AuthSession?
    fun update(session: AuthSession)

    /** Removes the session. [expired] distinguishes a forced sign-out from a user action. */
    fun clear(expired: Boolean)
}

/** Thrown inside OkHttp when the refresh token was rejected; surfaced as [ApiError.Unauthorized]. */
class SessionExpiredException : IOException("Session expired")
