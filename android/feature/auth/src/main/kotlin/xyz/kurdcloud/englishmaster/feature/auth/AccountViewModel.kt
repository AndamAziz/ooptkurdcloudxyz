package xyz.kurdcloud.englishmaster.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.AccountRepository
import xyz.kurdcloud.englishmaster.core.data.AuthRepository
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.model.CredentialRules
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

data class AccountUiState(
    val name: String? = null,
    val email: String = "",
    /** Highest role reported by the server; STUDENT until known. */
    val role: Role = Role.STUDENT,
    val savingName: Boolean = false,
    val deleting: Boolean = false,
    /** The account was deleted; the app returns to the welcome screen. */
    val deleted: Boolean = false,
    @StringRes val message: Int? = null,
) {
    val initials: String
        get() = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { email.take(1).uppercase() }

    /** Staff cannot delete their own account in the app (the server refuses it as well). */
    val canDeleteAccount: Boolean get() = role == Role.STUDENT
}

class AccountViewModel(
    private val auth: AuthRepository,
    private val learning: LearningRepository,
    private val account: AccountRepository,
    private val staff: StaffRepository? = null,
) : ViewModel() {
    private val state = MutableStateFlow(AccountUiState(email = auth.session.value?.email.orEmpty()))
    val uiState: StateFlow<AccountUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            (learning.profile() as? ApiResult.Success)?.value?.let { p -> state.update { it.copy(name = p.displayName) } }
        }
        viewModelScope.launch {
            val access = (staff?.myAccess() as? ApiResult.Success)?.value ?: return@launch
            val role = when {
                access.canManageUsers -> Role.ADMIN
                access.canReviewQuestions -> Role.TEACHER
                else -> Role.STUDENT
            }
            state.update { it.copy(role = role) }
        }
    }

    /** Saves the learner's name (used on future certificates). Returns false when it is not valid. */
    fun rename(newName: String): Boolean {
        if (!CredentialRules.isValidDisplayName(newName)) return false
        if (state.value.savingName) return true
        state.update { it.copy(savingName = true) }
        viewModelScope.launch {
            when (val r = learning.updateDisplayName(newName.trim())) {
                is ApiResult.Success -> state.update { it.copy(savingName = false, name = r.value, message = R.string.account_name_saved) }
                is ApiResult.Failure -> state.update { it.copy(savingName = false, message = r.error.accountMessage()) }
            }
        }
        return true
    }

    fun deleteAccount() {
        if (state.value.deleting) return
        state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val r = account.deleteAccount()) {
                is ApiResult.Success -> state.update { it.copy(deleting = false, deleted = true) }
                is ApiResult.Failure -> state.update { it.copy(deleting = false, message = r.error.accountMessage()) }
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.signOut()
            onDone()
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }
}

@StringRes
private fun ApiError.accountMessage(): Int = when {
    this == ApiError.Network -> R.string.account_error_network
    this is ApiError.Validation && hint == "staff_account" -> R.string.account_delete_staff
    else -> R.string.account_error_generic
}
