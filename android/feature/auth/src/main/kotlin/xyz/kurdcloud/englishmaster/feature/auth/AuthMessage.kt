package xyz.kurdcloud.englishmaster.feature.auth

import androidx.annotation.StringRes
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.AuthFailure

/** User-facing auth errors. Raw server messages are never displayed. */
enum class AuthMessage(@StringRes val text: Int) {
    INVALID_CREDENTIALS(R.string.auth_error_invalid_credentials),
    EMAIL_NOT_CONFIRMED(R.string.auth_error_email_not_confirmed),
    USER_EXISTS(R.string.auth_error_user_exists),
    WEAK_PASSWORD(R.string.auth_error_weak_password),
    SIGNUP_DISABLED(R.string.auth_error_signup_disabled),
    NETWORK(R.string.auth_error_network),
    RATE_LIMITED(R.string.auth_error_rate_limited),
    NOT_CONFIGURED(R.string.auth_error_not_configured),
    SESSION_EXPIRED(R.string.auth_error_session_expired),
    SAME_PASSWORD(R.string.auth_error_same_password),
    EMAIL_LIMIT(R.string.auth_error_email_limit),
    LINK_EXPIRED(R.string.auth_error_link_expired),
    LINK_INVALID(R.string.auth_error_link_invalid),
    CODE_INVALID(R.string.auth_error_code_invalid),
    GENERIC(R.string.auth_error_generic);

    companion object {
        fun from(error: ApiError): AuthMessage = when (error) {
            ApiError.Network -> NETWORK
            ApiError.RateLimited -> RATE_LIMITED
            ApiError.NotConfigured -> NOT_CONFIGURED
            is ApiError.Auth -> when (error.reason) {
                AuthFailure.INVALID_CREDENTIALS -> INVALID_CREDENTIALS
                AuthFailure.EMAIL_NOT_CONFIRMED -> EMAIL_NOT_CONFIRMED
                AuthFailure.USER_EXISTS -> USER_EXISTS
                AuthFailure.WEAK_PASSWORD -> WEAK_PASSWORD
                AuthFailure.SAME_PASSWORD -> SAME_PASSWORD
                AuthFailure.EMAIL_LIMIT -> EMAIL_LIMIT
                AuthFailure.SIGNUP_DISABLED -> SIGNUP_DISABLED
                AuthFailure.CODE_INVALID -> CODE_INVALID
                AuthFailure.OTHER -> GENERIC
            }
            else -> GENERIC
        }
    }
}
