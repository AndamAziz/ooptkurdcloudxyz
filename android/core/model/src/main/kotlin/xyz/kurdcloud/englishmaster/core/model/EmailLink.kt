package xyz.kurdcloud.englishmaster.core.model

/**
 * Links in Supabase Auth emails (password reset, sign-up confirmation) open the app at [REDIRECT].
 * The tokens arrive in the URL fragment: `#access_token=…&refresh_token=…&expires_in=…&type=recovery`,
 * or an error: `#error=access_denied&error_code=otp_expired&error_description=…`.
 */
sealed interface EmailLink {
    /** Signed in by the link; [recovery] means the user must now choose a new password. */
    data class SignedIn(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val recovery: Boolean,
    ) : EmailLink

    /** The link was used already or is too old. */
    data object Expired : EmailLink

    data object Invalid : EmailLink

    companion object {
        const val REDIRECT = "app.englishmaster://auth-callback"

        /** Parses an incoming link; null if it is not an auth callback for this app. */
        fun parse(uri: String): EmailLink? {
            if (!uri.startsWith(REDIRECT)) return null
            // Tokens normally come in the fragment; some clients move them to the query string.
            val params = uri.substringAfter('#', "").ifEmpty { uri.substringAfter('?', "") }
                .split('&')
                .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to decode(it[1]) } }
                .toMap()
            params["error_code"]?.let { return if (it == "otp_expired") Expired else Invalid }
            if (params["error"] != null) return Invalid
            val access = params["access_token"] ?: return Invalid
            val refresh = params["refresh_token"] ?: return Invalid
            return SignedIn(access, refresh, params["expires_in"]?.toLongOrNull() ?: 3600, params["type"] == "recovery")
        }

        /** URL-decodes ASCII values (tokens and codes are ASCII; '+' is a space). */
        private fun decode(value: String): String = buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when {
                    c == '+' -> append(' ')
                    c == '%' && i + 2 < value.length ->
                        value.substring(i + 1, i + 3).toIntOrNull(16)?.let { append(it.toChar()); i += 2 } ?: append(c)
                    else -> append(c)
                }
                i++
            }
        }
    }
}
