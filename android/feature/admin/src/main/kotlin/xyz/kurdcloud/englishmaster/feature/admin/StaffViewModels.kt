package xyz.kurdcloud.englishmaster.feature.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.model.ActivityEntry
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.Role
import xyz.kurdcloud.englishmaster.core.model.StaffQuestion
import xyz.kurdcloud.englishmaster.core.model.StaffUser
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

@StringRes
internal fun ApiError.message(): Int = when (this) {
    ApiError.Network -> R.string.admin_error_network
    ApiError.Forbidden -> R.string.admin_error_forbidden
    ApiError.Unauthorized -> R.string.admin_error_signed_out
    is ApiError.Validation -> R.string.admin_error_rejected
    else -> R.string.admin_error_generic
}

data class UsersUiState(
    val query: String = "",
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
    val users: List<StaffUser> = emptyList(),
    /** User whose row is being changed; its buttons are disabled meanwhile. */
    val busyUserId: String? = null,
    @StringRes val message: Int? = null,
)

class UsersViewModel(private val staff: StaffRepository) : ViewModel() {
    private val state = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = state.asStateFlow()

    init { load() }

    fun onQueryChange(value: String) = state.update { it.copy(query = value) }

    fun load() {
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = staff.users(state.value.query)) {
                is ApiResult.Success -> state.update { it.copy(loading = false, users = r.value) }
                is ApiResult.Failure -> state.update { it.copy(loading = false, error = r.error.message()) }
            }
        }
    }

    fun setRole(user: StaffUser, role: Role, granted: Boolean) = mutate(user) {
        when (val r = staff.setRole(user.userId, role, granted)) {
            is ApiResult.Success -> replace(user.copy(roles = r.value), R.string.admin_saved)
            is ApiResult.Failure -> fail(r.error)
        }
    }

    /** Corrects the learner's name; certificates already issued keep the old one. */
    fun setDisplayName(user: StaffUser, name: String) = mutate(user) {
        when (val r = staff.setDisplayName(user.userId, name)) {
            is ApiResult.Success -> replace(user.copy(displayName = r.value), R.string.admin_name_saved)
            is ApiResult.Failure -> fail(r.error)
        }
    }

    fun setSuspended(user: StaffUser, suspended: Boolean) = mutate(user) {
        when (val r = staff.setSuspended(user.userId, suspended)) {
            is ApiResult.Success -> replace(user.copy(suspended = r.value), R.string.admin_saved)
            is ApiResult.Failure -> fail(r.error)
        }
    }

    fun messageShown() = state.update { it.copy(message = null) }

    private fun mutate(user: StaffUser, block: suspend () -> Unit) {
        if (state.value.busyUserId != null) return
        state.update { it.copy(busyUserId = user.userId) }
        viewModelScope.launch {
            block()
            state.update { it.copy(busyUserId = null) }
        }
    }

    private fun replace(updated: StaffUser, @StringRes message: Int) = state.update { s ->
        s.copy(users = s.users.map { if (it.userId == updated.userId) updated else it }, message = message)
    }

    private fun fail(error: ApiError) = state.update { it.copy(message = error.message()) }
}

data class QuestionsUiState(
    val level: CefrLevel = CefrLevel.A1,
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
    val questions: List<StaffQuestion> = emptyList(),
    val busyQuestionId: String? = null,
    @StringRes val message: Int? = null,
    val publishing: Boolean = false,
) {
    val drafts: Int get() = questions.count { it.status == "draft" && it.reviewStatus == "unreviewed" }
}

class QuestionsViewModel(private val staff: StaffRepository) : ViewModel() {
    private val state = MutableStateFlow(QuestionsUiState())
    val uiState: StateFlow<QuestionsUiState> = state.asStateFlow()

    init { load(CefrLevel.A1) }

    fun load(level: CefrLevel) {
        state.update { it.copy(level = level, loading = true, error = null) }
        viewModelScope.launch {
            when (val r = staff.questions(level)) {
                is ApiResult.Success -> if (state.value.level == level) state.update { it.copy(loading = false, questions = r.value) }
                is ApiResult.Failure -> state.update { it.copy(loading = false, error = r.error.message()) }
            }
        }
    }

    /** Approving a draft (e.g. an AI-drafted item) also publishes it, so students can practise it. */
    fun setApproved(question: StaffQuestion, approved: Boolean) =
        review(question, approved, status = if (approved && question.status == "draft") "published" else null)

    /** Retired questions can no longer be downloaded or answered by students. */
    fun setRetired(question: StaffQuestion, retired: Boolean) =
        review(question, question.reviewStatus == "approved", status = if (retired) "retired" else "published")

    fun messageShown() = state.update { it.copy(message = null) }

    /** Approves and publishes every unreviewed draft of the selected level, then reloads. */
    fun publishAllDrafts() {
        val level = state.value.level
        if (state.value.publishing) return
        state.update { it.copy(publishing = true) }
        viewModelScope.launch {
            val message = when (val r = staff.publishDrafts(level)) {
                is ApiResult.Success -> R.string.admin_drafts_published
                is ApiResult.Failure -> r.error.message()
            }
            state.update { it.copy(publishing = false, message = message) }
            load(level)
        }
    }

    private fun review(question: StaffQuestion, approved: Boolean, status: String?) {
        if (state.value.busyQuestionId != null) return
        state.update { it.copy(busyQuestionId = question.id) }
        viewModelScope.launch {
            when (val r = staff.review(question.id, approved, status)) {
                is ApiResult.Success -> state.update { s ->
                    s.copy(
                        questions = s.questions.map {
                            if (it.id == question.id) it.copy(reviewStatus = r.value.reviewStatus, status = r.value.status) else it
                        },
                        message = R.string.admin_saved,
                    )
                }
                is ApiResult.Failure -> state.update { it.copy(message = r.error.message()) }
            }
            state.update { it.copy(busyQuestionId = null) }
        }
    }
}

data class ActivityUiState(
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
    val entries: List<ActivityEntry> = emptyList(),
)

class ActivityViewModel(private val staff: StaffRepository) : ViewModel() {
    private val state = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = state.asStateFlow()

    init { load() }

    fun load() {
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = staff.recentActivity()) {
                is ApiResult.Success -> state.update { it.copy(loading = false, entries = r.value) }
                is ApiResult.Failure -> state.update { it.copy(loading = false, error = r.error.message()) }
            }
        }
    }
}
