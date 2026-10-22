package xyz.kurdcloud.englishmaster.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.model.Topic

data class SessionSummary(val total: Int, val correct: Int, val incorrect: Int, val pending: Int)

data class PracticeUiState(
    val loading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val submitting: Boolean = false,
    val answers: Map<String, PracticeAnswer> = emptyMap(),
    val finished: Boolean = false,
) {
    val current: Question? get() = questions.getOrNull(index)
    val currentAnswer: PracticeAnswer? get() = current?.let { answers[it.id] }
    val isEmpty: Boolean get() = !loading && questions.isEmpty()

    val summary: SessionSummary
        get() {
            val values = answers.values
            return SessionSummary(
                total = values.size,
                correct = values.count { it.syncState == SyncState.SYNCED && it.isCorrect == true },
                incorrect = values.count { it.syncState == SyncState.SYNCED && it.isCorrect == false },
                pending = values.count { it.syncState == SyncState.PENDING },
            )
        }
}

class PracticeViewModel(
    /** Level to practise; ignored for a [topic] session, which draws on every downloaded level. */
    private val level: CefrLevel?,
    private val userId: String,
    private val content: ContentRepository,
    private val practice: PracticeRepository,
    private val sessionSize: Int = 10,
    /** Review session: only questions whose latest marked answer was wrong. */
    val mistakesOnly: Boolean = false,
    /** Topic session (Life in the UK, Britain, ...) across all downloaded levels. */
    val topic: Topic? = null,
    /** Session on one exact tag (a Life in the UK chapter) across all downloaded levels. */
    private val tag: String? = null,
) : ViewModel() {
    private val attemptId = practice.newAttemptId()
    private val state = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = when {
                tag != null -> content.pickTagged(tag, userId, sessionSize)
                topic != null -> content.pickTopic(topic, userId, sessionSize)
                level == null -> emptyList()
                mistakesOnly -> content.pickMistakes(level, userId, sessionSize)
                else -> content.pickSession(level, userId, sessionSize)
            }
            state.update { it.copy(loading = false, questions = questions) }
        }
        // Answers saved offline flip from "pending" to scored as soon as background sync lands.
        viewModelScope.launch {
            practice.observeAttempt(attemptId).collect { rows ->
                state.update { s -> s.copy(answers = rows.associateBy { it.questionId }) }
            }
        }
    }

    fun select(optionIndex: Int) {
        if (state.value.currentAnswer == null) state.update { it.copy(selected = optionIndex) }
    }

    fun submit() {
        val s = state.value
        val question = s.current ?: return
        val option = s.selected ?: return
        if (s.submitting || s.currentAnswer != null) return
        state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val answer = practice.answer(userId, attemptId, question, option)
            state.update { it.copy(submitting = false, answers = it.answers + (question.id to answer)) }
        }
    }

    fun next() {
        state.update {
            if (it.index + 1 >= it.questions.size) it.copy(finished = true)
            else it.copy(index = it.index + 1, selected = null)
        }
    }
}
