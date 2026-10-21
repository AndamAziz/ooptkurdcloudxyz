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
