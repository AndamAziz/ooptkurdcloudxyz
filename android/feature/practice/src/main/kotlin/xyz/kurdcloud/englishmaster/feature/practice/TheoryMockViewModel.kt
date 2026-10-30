package xyz.kurdcloud.englishmaster.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.TheoryRepository
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheoryTest
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem

enum class TheoryMockPhase { LOADING, EMPTY, RUNNING, RESULT }

data class TheoryMockUiState(
    val phase: TheoryMockPhase = TheoryMockPhase.LOADING,
    val items: List<TheoryTestItem> = emptyList(),
    val index: Int = 0,
    /** Question id -> the option the learner picked; nothing is marked until the paper is handed in. */
    val choices: Map<String, Int> = emptyMap(),
    val remainingSeconds: Long = TheoryTest.MINUTES * 60L,
    /** True when the time ran out and the paper was handed in for the learner. */
    val autoSubmitted: Boolean = false,
    /** True while the chapters are being fetched because they were not on the device yet. */
    val downloading: Boolean = false,
) {
    val item: TheoryTestItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
    val answeredCount: Int get() = choices.size
    val unanswered: Int get() = total - answeredCount
    val isLast: Boolean get() = index + 1 >= total

    /** Unanswered questions are wrong, as they are in the real test. */
    val correct: Int get() = items.count { choices[it.question.id] == it.correctIndex }
    val passMark: Int get() = TheoryTest.passMarkFor(total)
    val passed: Boolean get() = correct >= passMark
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total

    /** The questions to go over afterwards: wrong, or left blank. */
    val wrong: List<TheoryTestItem> get() = items.filter { choices[it.question.id] != it.correctIndex }
}

/**
 * A mock driving theory exam in the official format: [TheoryTest.QUESTIONS] questions spread over
 * every chapter, [TheoryTest.MINUTES] minutes, pass mark [TheoryTest.PASS_MARK], and no feedback
 * until the paper is handed in.
 *
 * The app marks it itself, because the questions carry their own answer, so the result is instant
 * and works offline. Answers are recorded through the normal queue afterwards, so a sitting counts
 * towards each chapter's standing like any other practice.
 */
class TheoryMockViewModel(
    private val theory: TheoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state = MutableStateFlow(TheoryMockUiState())
    val uiState: StateFlow<TheoryMockUiState> = state.asStateFlow()
    private var timer: Job? = null

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            var items = theory.mockTest()
            if (items.size < TheoryTest.QUESTIONS) {
                // A paper covers the whole course, so any chapter still missing is fetched first.
                state.update { it.copy(downloading = true) }
                downloadMissingChapters()
                state.update { it.copy(downloading = false) }
                items = theory.mockTest()
            }
            if (items.isEmpty()) {
                state.update { it.copy(phase = TheoryMockPhase.EMPTY) }
            } else {
                state.update { it.copy(phase = TheoryMockPhase.RUNNING, items = items) }
                startTimer(clock() + minutesFor(items.size) * 60_000L)
            }
        }
    }

    /** A short paper gets proportionally less time, so the pace matches the real test. */
    private fun minutesFor(total: Int): Long =
        if (total >= TheoryTest.QUESTIONS) TheoryTest.MINUTES.toLong()
        else (total.toLong() * TheoryTest.MINUTES + TheoryTest.QUESTIONS - 1) / TheoryTest.QUESTIONS

    private suspend fun downloadMissingChapters() {
        theory.refreshSections()
        val saved = theory.observeDownloadCounts().first()
        theory.observeSections().first()
            .filter { !it.isEmpty && (saved[it.slug] ?: 0) == 0 }
            .forEach { theory.downloadSection(it.slug) }
    }

    private fun startTimer(endsAt: Long) {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (isActive) {
                val left = ((endsAt - clock()) / 1000).coerceAtLeast(0)
                state.update { it.copy(remainingSeconds = left) }
                if (left == 0L) {
                    submit(auto = true)
                    break
                }
                delay(1_000)
            }
        }
    }

    /** Picks an option. Nothing is marked yet, and the learner may change their mind. */
    fun select(option: Int) {
        val s = state.value
        if (s.phase != TheoryMockPhase.RUNNING) return
        val id = s.item?.question?.id ?: return
        state.update { it.copy(choices = it.choices + (id to option)) }
    }

    fun goTo(index: Int) = state.update { if (index in 0 until it.total) it.copy(index = index) else it }
    fun next() = goTo(state.value.index + 1)
    fun previous() = goTo(state.value.index - 1)

    /** Hands the paper in and marks it. Unanswered questions count as wrong. */
    fun submit(auto: Boolean = false) {
        val s = state.value
        if (s.phase != TheoryMockPhase.RUNNING) return
        timer?.cancel()
        state.update { it.copy(phase = TheoryMockPhase.RESULT, autoSubmitted = auto) }
        viewModelScope.launch {
            // Only questions the learner actually answered go into their standing: a blank one was
            // never attempted, and marking it wrong would misrepresent the chapter.
            s.items.forEach { item ->
                val choice = s.choices[item.question.id] ?: return@forEach
                theory.record(item.question, choice == item.correctIndex, TheoryMode.TEST)
            }
        }
    }

    /** Sits a fresh paper, with a new mix of questions. */
    fun again() {
        timer?.cancel()
        state.value = TheoryMockUiState()
        start()
    }
}
