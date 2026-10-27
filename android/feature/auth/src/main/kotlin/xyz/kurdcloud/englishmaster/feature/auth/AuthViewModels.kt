package xyz.kurdcloud.englishmaster.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.AuthRepository
import xyz.kurdcloud.englishmaster.core.data.RegisterOutcome
import xyz.kurdcloud.englishmaster.core.model.CredentialRules
import xyz.kurdcloud.englishmaster.core.network.ApiResult

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val submitting: Boolean = false,
    val message: AuthMessage? = null,
    val signedIn: Boolean = false,
)

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    fun onEmailChange(value: String) = state.update { it.copy(email = value, emailError = false, message = null) }
    fun onPasswordChange(value: String) = state.update { it.copy(password = value, passwordError = false, message = null) }
    fun showMessage(message: AuthMessage) = state.update { it.copy(message = message) }

    fun submit() {
        val s = state.value
        if (s.submitting) return
        val emailError = !CredentialRules.isValidEmail(s.email)
        val passwordError = s.password.isEmpty()
        if (emailError || passwordError) {
            state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = auth.signIn(s.email, s.password)) {
                is ApiResult.Success -> state.update { it.copy(submitting = false, signedIn = true, password = "") }
                is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(result.error)) }
            }
        }
    }
}

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val consent: Boolean = false,
    val nameError: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val consentError: Boolean = false,
    val submitting: Boolean = false,
    val message: AuthMessage? = null,
    val outcome: RegisterOutcome? = null,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = state.asStateFlow()

    fun onNameChange(value: String) = state.update { it.copy(displayName = value, nameError = false, message = null) }
    fun onEmailChange(value: String) = state.update { it.copy(email = value, emailError = false, message = null) }
    fun onPasswordChange(value: String) = state.update { it.copy(password = value, passwordError = false, message = null) }
    fun onConsentChange(value: Boolean) = state.update { it.copy(consent = value, consentError = false) }

    fun submit() {
        val s = state.value
        if (s.submitting) return
        val errors = s.copy(
            nameError = !CredentialRules.isValidDisplayName(s.displayName),
            emailError = !CredentialRules.isValidEmail(s.email),
            passwordError = !CredentialRules.isValidPassword(s.password),
            consentError = !s.consent,
        )
        if (errors.nameError || errors.emailError || errors.passwordError || errors.consentError) {
            state.value = errors
            return
        }
        state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = auth.register(s.displayName, s.email, s.password)) {
                is ApiResult.Success -> state.update { it.copy(submitting = false, outcome = result.value, password = "") }
                is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(result.error)) }
            }
        }
    }
}

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: Boolean = false,
    val submitting: Boolean = false,
    val message: AuthMessage? = null,
    val sent: Boolean = false,
)

class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = state.asStateFlow()

    fun onEmailChange(value: String) = state.update { it.copy(email = value, emailError = false, message = null) }

    fun submit() {
        val s = state.value
        if (s.submitting) return
        if (!CredentialRules.isValidEmail(s.email)) {
            state.update { it.copy(emailError = true) }
            return
        }
        state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = auth.requestPasswordReset(s.email)) {
                // Same confirmation whether or not the address has an account (no account enumeration).
                is ApiResult.Success -> state.update { it.copy(submitting = false, sent = true) }
                is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(result.error)) }
            }
        }
    }
}

data class ResetCodeUiState(
    val code: String = "",
    val password: String = "",
    val confirm: String = "",
    val codeError: Boolean = false,
    val passwordError: Boolean = false,
    val mismatch: Boolean = false,
    val submitting: Boolean = false,
    val message: AuthMessage? = null,
    val done: Boolean = false,
)

/**
 * Password reset with the code from the email: the code signs the user in, then the new password is
 * saved. Browsers do not reliably open the app from the link in the same email, a code always works.
 */
class ResetCodeViewModel(private val email: String, private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(ResetCodeUiState())
    val uiState: StateFlow<ResetCodeUiState> = state.asStateFlow()

    /** Keeps digits only: mail clients may show the code with spaces. */
    fun onCodeChange(value: String) =
        state.update { it.copy(code = value.filter(Char::isDigit).take(MAX_CODE_LENGTH), codeError = false, message = null) }

    fun onPasswordChange(value: String) = state.update { it.copy(password = value, passwordError = false, mismatch = false, message = null) }
    fun onConfirmChange(value: String) = state.update { it.copy(confirm = value, mismatch = false, message = null) }

    fun submit() {
        val s = state.value
        if (s.submitting) return
        if (s.code.length < MIN_CODE_LENGTH) {
            state.update { it.copy(codeError = true) }
            return
        }
        if (!CredentialRules.isValidPassword(s.password)) {
            state.update { it.copy(passwordError = true) }
            return
        }
        if (s.password != s.confirm) {
            state.update { it.copy(mismatch = true) }
            return
        }
        state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val verified = auth.verifyRecoveryCode(email, s.code)) {
                is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(verified.error)) }
                is ApiResult.Success -> when (val saved = auth.updatePassword(s.password)) {
                    is ApiResult.Success -> state.update { it.copy(submitting = false, done = true, password = "", confirm = "") }
                    is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(saved.error)) }
                }
            }
        }
    }

    companion object {
        /** The code's length is a server setting (six by default), so any plausible length is accepted. */
        const val MIN_CODE_LENGTH = 6
        const val MAX_CODE_LENGTH = 12
    }
}

data class SetPasswordUiState(
    val password: String = "",
    val confirm: String = "",
    val passwordError: Boolean = false,
    val mismatch: Boolean = false,
    val submitting: Boolean = false,
    val message: AuthMessage? = null,
    val done: Boolean = false,
)

/** After a password-reset link signed the user in: choose the new password. */
class SetPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(SetPasswordUiState())
    val uiState: StateFlow<SetPasswordUiState> = state.asStateFlow()

    fun onPasswordChange(value: String) = state.update { it.copy(password = value, passwordError = false, mismatch = false, message = null) }
    fun onConfirmChange(value: String) = state.update { it.copy(confirm = value, mismatch = false, message = null) }

    fun submit() {
        val s = state.value
        if (s.submitting) return
        if (!CredentialRules.isValidPassword(s.password)) {
            state.update { it.copy(passwordError = true) }
            return
        }
        if (s.password != s.confirm) {
            state.update { it.copy(mismatch = true) }
            return
        }
        state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = auth.updatePassword(s.password)) {
                is ApiResult.Success -> state.update { it.copy(submitting = false, done = true, password = "", confirm = "") }
                is ApiResult.Failure -> state.update { it.copy(submitting = false, message = AuthMessage.from(result.error)) }
            }
        }
    }
}
