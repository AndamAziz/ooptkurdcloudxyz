package xyz.kurdcloud.englishmaster.feature.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.CredentialRules
import xyz.kurdcloud.englishmaster.core.model.ExamKind
import xyz.kurdcloud.englishmaster.core.model.ExamRefusal
import xyz.kurdcloud.englishmaster.core.model.ExamResult
import xyz.kurdcloud.englishmaster.core.model.ExamSession
import xyz.kurdcloud.englishmaster.core.model.PlacementResult
import xyz.kurdcloud.englishmaster.core.model.optionLetter
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

enum class ExamPhase { INTRO, STARTING, RUNNING, SUBMITTING, FINISHED }

/** Problems shown to the learner. */
enum class ExamProblem {
    OFFLINE, SIGN_IN_AGAIN, NOT_UNLOCKED, RETRY_LATER, ALREADY_CERTIFIED, NOT_ENOUGH_QUESTIONS, INVALID_NAME, UNEXPECTED;

    companion object {
        fun from(error: ApiError): ExamProblem = when (error) {
            ApiError.Network, ApiError.RateLimited, is ApiError.Server -> OFFLINE
            ApiError.Unauthorized, ApiError.Forbidden -> SIGN_IN_AGAIN
            is ApiError.Validation -> when (ExamRefusal.fromHint(error.hint)) {
                ExamRefusal.NOT_UNLOCKED -> NOT_UNLOCKED
                ExamRefusal.RETRY_LATER -> RETRY_LATER
                ExamRefusal.ALREADY_CERTIFIED -> ALREADY_CERTIFIED
                ExamRefusal.NOT_ENOUGH_QUESTIONS -> NOT_ENOUGH_QUESTIONS
                else -> UNEXPECTED
            }
            else -> UNEXPECTED
        }
    }
}

data class ExamUiState(
    val kind: ExamKind,
    val level: CefrLevel?,
    val phase: ExamPhase = ExamPhase.INTRO,
    /** Name printed on the certificate (level exams); null while loading or offline. */
    val displayName: String? = null,
    val session: ExamSession? = null,
    val index: Int = 0,
    /** Question id -> chosen option index. */
    val answers: Map<String, Int> = emptyMap(),
    val remainingSeconds: Long = 0,
    val result: ExamResult? = null,
    val placement: PlacementResult? = null,
    val problem: ExamProblem? = null,
    /** True when the time ran out and the answers were sent automatically. */
    val autoSubmitted: Boolean = false,
) {
    val question get() = session?.questions?.getOrNull(index)
    val total: Int get() = session?.questions?.size ?: 0
    val answeredCount: Int get() = answers.size
    val unanswered: Int get() = total - answeredCount
}

class ExamViewModel(
    kind: ExamKind,
    level: CefrLevel?,
    private val repo: LearningRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state = MutableStateFlow(ExamUiState(kind, level))
    val uiState: StateFlow<ExamUiState> = state.asStateFlow()
    private var timer: Job? = null

    init {
        if (kind == ExamKind.LEVEL_FINAL) loadName()
    }

    private fun loadName() = viewModelScope.launch {
        (repo.profile() as? ApiResult.Success)?.let { r -> state.update { it.copy(displayName = r.value.displayName) } }
    }

    fun updateName(name: String) {
        if (!CredentialRules.isValidDisplayName(name)) {
            state.update { it.copy(problem = ExamProblem.INVALID_NAME) }
            return
        }
        viewModelScope.launch {
            when (val r = repo.updateDisplayName(name)) {
                is ApiResult.Success -> state.update { it.copy(displayName = r.value, problem = null) }
                is ApiResult.Failure -> state.update { it.copy(problem = ExamProblem.from(r.error)) }
            }
        }
    }

    fun start() {
        val s = state.value
        if (s.phase != ExamPhase.INTRO) return
        state.update { it.copy(phase = ExamPhase.STARTING, problem = null) }
        viewModelScope.launch {
            val r = if (s.kind == ExamKind.PLACEMENT) repo.startPlacement() else repo.startLevelExam(checkNotNull(s.level))
            when (r) {
                is ApiResult.Success -> {
                    state.update { it.copy(phase = ExamPhase.RUNNING, session = r.value, index = 0, answers = emptyMap()) }
                    startTimer(r.value.expiresAtMillis)
                }
                is ApiResult.Failure -> state.update { it.copy(phase = ExamPhase.INTRO, problem = ExamProblem.from(r.error)) }
            }
        }
    }

    private fun startTimer(expiresAt: Long) {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (isActive) {
                val left = ((expiresAt - clock()) / 1000).coerceAtLeast(0)
                state.update { it.copy(remainingSeconds = left) }
                if (left == 0L) {
                    submit(auto = true)
                    break
                }
                delay(1_000)
            }
        }
    }

    fun select(optionIndex: Int) {
        val q = state.value.question ?: return
        if (state.value.phase != ExamPhase.RUNNING) return
        state.update { it.copy(answers = it.answers + (q.id to optionIndex)) }
    }

    fun goTo(index: Int) = state.update { if (index in 0 until it.total) it.copy(index = index) else it }
    fun next() = goTo(state.value.index + 1)
    fun previous() = goTo(state.value.index - 1)

    fun submit(auto: Boolean = false) {
        val s = state.value
        val session = s.session ?: return
        if (s.phase != ExamPhase.RUNNING) return
        state.update { it.copy(phase = ExamPhase.SUBMITTING, problem = null, autoSubmitted = auto) }
        val letters = s.answers.mapValues { optionLetter(it.value) }
        viewModelScope.launch {
            if (session.kind == ExamKind.PLACEMENT) {
                when (val r = repo.submitPlacement(session.attemptId, letters)) {
                    is ApiResult.Success -> finish { it.copy(placement = r.value) }
                    is ApiResult.Failure -> submitFailed(r.error)
                }
            } else {
                when (val r = repo.submitLevelExam(session.attemptId, letters)) {
                    is ApiResult.Success -> finish { it.copy(result = r.value) }
                    is ApiResult.Failure -> submitFailed(r.error)
                }
            }
        }
    }

    private fun finish(apply: (ExamUiState) -> ExamUiState) {
        timer?.cancel()
        state.update { apply(it.copy(phase = ExamPhase.FINISHED)) }
    }

    /** Answers stay on screen; the learner can retry (the server accepts a short grace period). */
    private fun submitFailed(error: ApiError) =
        state.update { it.copy(phase = ExamPhase.RUNNING, problem = ExamProblem.from(error)) }

    fun problemShown() = state.update { it.copy(problem = null) }
}
