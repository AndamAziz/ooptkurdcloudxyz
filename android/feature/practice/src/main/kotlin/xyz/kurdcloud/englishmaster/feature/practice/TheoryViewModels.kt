package xyz.kurdcloud.englishmaster.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.TheoryRepository
import xyz.kurdcloud.englishmaster.core.model.TheoryMode
import xyz.kurdcloud.englishmaster.core.model.TheorySection
import xyz.kurdcloud.englishmaster.core.model.TheoryTest
import xyz.kurdcloud.englishmaster.core.model.TheoryTestItem
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

/** How far a chapter's download has got: [done] of [total] chapters. */
data class TheoryDownload(val done: Int, val total: Int)

data class TheoryUiState(
    val isOnline: Boolean = true,
    val loading: Boolean = true,
    val sections: List<TheorySection> = emptyList(),
    /** Questions saved on the device, per chapter slug. */
    val saved: Map<String, Int> = emptyMap(),
    val downloading: TheoryDownload? = null,
    /** Answers waiting to reach the server. */
    val pending: Int = 0,
    val message: UiMessage? = null,
) {
    val answered: Int get() = sections.sumOf { it.answered }
    val published: Int get() = sections.sumOf { it.published }
    val correct: Int get() = sections.sumOf { it.correct }
    val accuracyPercent: Int get() = if (answered == 0) 0 else correct * 100 / answered

    /** Chapters that have questions on the server but are not saved on the device yet. */
    val missing: List<TheorySection> get() = sections.filter { !it.isEmpty && (saved[it.slug] ?: 0) == 0 }
    fun isSaved(section: TheorySection): Boolean = (saved[section.slug] ?: 0) > 0
}

/**
 * The driving theory course page: the chapters of the official test, each with the learner's
 * standing, and the download that makes them work offline.
 */
class TheoryViewModel(
    private val theory: TheoryRepository,
    network: NetworkMonitor,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val online = network.isOnline
    private var job: Job? = null

    private data class Transient(
        val loading: Boolean = true,
        val downloading: TheoryDownload? = null,
        val message: UiMessage? = null,
    )

    val uiState: StateFlow<TheoryUiState> = combine(
        theory.observeSections(),
        theory.observeDownloadCounts(),
        theory.observePendingCount(),
        online,
        transient,
    ) { sections, saved, pending, isOnline, t ->
        TheoryUiState(
            isOnline = isOnline,
            loading = t.loading && sections.isEmpty(),
            sections = sections,
            saved = saved,
            downloading = t.downloading,
            pending = pending,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TheoryUiState())

    init {
        refresh()
    }

    /** Re-reads the chapter list and standings from the server; silent when offline. */
    fun refresh() {
        viewModelScope.launch {
            theory.syncNow()
            theory.refreshSections()
            transient.update { it.copy(loading = false) }
        }
    }

    /** Downloads every chapter that has questions, so the whole course works offline. */
    fun downloadAll() {
        if (job?.isActive == true) return
        if (!online.value) {
            transient.update { it.copy(message = UiMessage(R.string.practice_download_needs_internet)) }
            return
        }
        job = viewModelScope.launch {
            try {
                if (theory.refreshSections() is ApiResult.Failure) {
                    transient.update { it.copy(message = UiMessage(R.string.practice_download_failed_network)) }
                    return@launch
                }
                val wanted = uiState.value.sections.filter { !it.isEmpty }
                var questions = 0
                wanted.forEachIndexed { index, section ->
                    transient.update { it.copy(downloading = TheoryDownload(index, wanted.size)) }
                    when (val r = theory.downloadSection(section.slug)) {
                        is ApiResult.Success -> questions += r.value
                        is ApiResult.Failure -> {
                            transient.update {
                                it.copy(
                                    message = UiMessage(
                                        if (r.error == ApiError.Network) R.string.practice_download_failed_network
                                        else R.string.practice_download_failed,
                                    ),
                                )
                            }
                            return@launch
                        }
                    }
                }
                transient.update { it.copy(message = UiMessage(R.plurals.theory_download_done, questions, plural = true)) }
            } finally {
                transient.update { it.copy(downloading = null) }
            }
        }
    }

    /** Forgets the learner's answers in one chapter, so it can be studied again. */
    fun resetSection(slug: String) {
        viewModelScope.launch {
            theory.resetSection(slug)
            transient.update { it.copy(message = UiMessage(R.string.theory_reset_done)) }
        }
    }

    fun messageShown() = transient.update { it.copy(message = null) }
}

enum class TheoryPhase { LOADING, EMPTY, RUNNING, DONE }

data class TheorySessionUiState(
    val phase: TheoryPhase = TheoryPhase.LOADING,
    val items: List<TheoryTestItem> = emptyList(),
    val index: Int = 0,
    /** In study mode: whether the answer on the current card is showing. */
    val revealed: Boolean = false,
    /** In test mode: the option the learner tapped, null before they answer. */
    val chosen: Int? = null,
    val correct: Int = 0,
    /** True while the chapter is being fetched because it was not on the device. */
    val downloading: Boolean = false,
) {
    val item: TheoryTestItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
    val answeredCount: Int get() = index + if (chosen != null || revealed) 1 else 0
    val isLast: Boolean get() = index + 1 >= total
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

/**
 * One run through a chapter, in either mode.
 *
 * Study mode shows the question, the learner thinks, reveals the answer and says whether they knew
 * it. Test mode shows the same question with four options, three of them answers borrowed from
 * other questions in the chapter. Both record the outcome, so a chapter's standing is the same
 * however it was practised.
 */
class TheorySessionViewModel(
    private val slug: String,
    val mode: TheoryMode,
    private val theory: TheoryRepository,
) : ViewModel() {
    private val state = MutableStateFlow(TheorySessionUiState())
    val uiState: StateFlow<TheorySessionUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            var items = load()
            if (items.isEmpty()) {
                // Not downloaded yet (or the chapter was opened straight from the list).
                state.update { it.copy(downloading = true) }
                theory.downloadSection(slug)
                state.update { it.copy(downloading = false) }
                items = load()
            }
            state.update {
                it.copy(phase = if (items.isEmpty()) TheoryPhase.EMPTY else TheoryPhase.RUNNING, items = items)
            }
        }
    }

    private suspend fun load(): List<TheoryTestItem> = when (mode) {
        // Study mode needs no options; one card per question keeps the two modes on the same state.
        TheoryMode.STUDY -> theory.deck(slug).take(TheoryTest.ROUND).map(TheoryTestItem::card)
        TheoryMode.TEST -> theory.testRound(slug, TheoryTest.ROUND)
    }

    /** Study mode: turns the card over. */
    fun reveal() {
        if (state.value.phase == TheoryPhase.RUNNING) state.update { it.copy(revealed = true) }
    }

    /** Study mode: the learner says whether they knew the answer. */
    fun knew(knew: Boolean) = record(knew)

    /** Test mode: picks an option, which marks the question at once. */
    fun choose(option: Int) {
        val s = state.value
        if (s.phase != TheoryPhase.RUNNING || s.chosen != null) return
        val item = s.item ?: return
        state.update { it.copy(chosen = option) }
        record(option == item.correctIndex, advance = false)
    }

    private fun record(isCorrect: Boolean, advance: Boolean = true) {
        val item = state.value.item ?: return
        if (isCorrect) state.update { it.copy(correct = it.correct + 1) }
        viewModelScope.launch { theory.record(item.question, isCorrect, mode) }
        if (advance) next()
    }

    fun next() = state.update {
        if (it.isLast) it.copy(phase = TheoryPhase.DONE) else it.copy(index = it.index + 1, revealed = false, chosen = null)
    }

    /** Starts the same chapter again, with a fresh set of borrowed options. */
    fun again() {
        state.value = TheorySessionUiState()
        viewModelScope.launch {
            val items = load()
            state.update { it.copy(phase = if (items.isEmpty()) TheoryPhase.EMPTY else TheoryPhase.RUNNING, items = items) }
        }
    }
}
