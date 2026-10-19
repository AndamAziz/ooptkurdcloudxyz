package xyz.kurdcloud.englishmaster.core.model

/** Tokens issued by Supabase Auth. Stored encrypted on the device. */
data class AuthSession(
    val userId: String,
    val email: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    fun expiresWithin(seconds: Long, nowEpochSeconds: Long): Boolean =
        expiresAtEpochSeconds - nowEpochSeconds <= seconds

    override fun toString(): String = "AuthSession(userId=$userId, expiresAt=$expiresAtEpochSeconds)"
}

/** Client-side input checks. The server remains the authority. */
object CredentialRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DISPLAY_NAME_LENGTH = 80
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean = email.length <= 254 && EMAIL.matches(email.trim())

    fun isValidPassword(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH && password.any { it.isLetter() } && password.any { it.isDigit() }

    fun isValidDisplayName(name: String): Boolean = name.trim().length in 1..MAX_DISPLAY_NAME_LENGTH
}
