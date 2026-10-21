package xyz.kurdcloud.englishmaster.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.LifeInUkChapter
import xyz.kurdcloud.englishmaster.core.model.LifeInUkTest
import xyz.kurdcloud.englishmaster.core.model.PracticeAnswer
import xyz.kurdcloud.englishmaster.core.model.Question
import xyz.kurdcloud.englishmaster.core.model.SyncState
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

data class LifeInUkUiState(
    val isOnline: Boolean = true,
    val pack: LevelPack? = null,
    /** Questions received so far while a download runs; null when idle. */
    val downloading: Int? = null,
    /** Downloaded questions per handbook chapter. */
    val chapters: Map<LifeInUkChapter, Int> = emptyMap(),
    val progress: LevelProgress? = null,
    /** Downloaded questions whose latest marked answer was wrong. */
    val mistakes: Int = 0,
    val message: UiMessage? = null,
) {
    val questionCount: Int get() = pack?.questionCount ?: 0
    val canTakeMockTest: Boolean get() = questionCount >= LifeInUkTest.QUESTIONS
}

/** The Life in the UK course page: download the question pack, practise by chapter, mock tests. */
class LifeInUkViewModel(
    userId: String,
    private val content: ContentRepository,
    practice: PracticeRepository,
    network: NetworkMonitor,
) : ViewModel() {
    private val transient = MutableStateFlow(Pair<Int?, UiMessage?>(null, null))
    private var downloadJob: Job? = null
    private val online = network.isOnline

    val uiState: StateFlow<LifeInUkUiState> = combine(
        combine(content.observePacks(), practice.observeProgress(userId), content.observeMistakeCounts(userId)) { packs, progress, mistakes ->
            Triple(
                packs.find { it.level == CefrLevel.LIFE_IN_UK },
                progress.find { it.level == CefrLevel.LIFE_IN_UK },
                mistakes[CefrLevel.LIFE_IN_UK] ?: 0,
            )
        },
        content.observeTagCounts(LifeInUkChapter.entries.map { it.tag }),
        network.isOnline,
        transient,
    ) { (pack, progress, mistakes), tagCounts, isOnline, (downloading, message) ->
        LifeInUkUiState(
            isOnline = isOnline,
            pack = pack,
            downloading = downloading,
            chapters = LifeInUkChapter.entries.associateWith { tagCounts[it.tag] ?: 0 },
            progress = progress,
            mistakes = mistakes,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeInUkUiState())

    /** Downloads (or updates) the Life in the UK questions. */
    fun download() {
        if (downloadJob?.isActive == true) return
        if (!online.value) {
            transient.update { it.copy(second = UiMessage(R.string.practice_download_needs_internet)) }
            return
        }
        transient.value = 0 to null
        downloadJob = viewModelScope.launch {
            try {
                val message = when (val r = content.downloadLevel(CefrLevel.LIFE_IN_UK) { n -> transient.update { it.copy(first = n) } }) {
                    is ApiResult.Success -> UiMessage(R.plurals.practice_download_done, r.value, plural = true)
                    is ApiResult.Failure -> UiMessage(
                        if (r.error == ApiError.Network) R.string.practice_download_failed_network else R.string.practice_download_failed,
                    )
                }
                transient.update { it.copy(second = message) }
            } finally {
                transient.update { it.copy(first = null) }
            }
        }
    }

    fun messageShown() = transient.update { it.copy(second = null) }
}

enum class MockPhase { LOADING, NOT_ENOUGH, RUNNING, MARKING, RESULT }

data class MockTestUiState(
    val phase: MockPhase = MockPhase.LOADING,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    /** Question id -> chosen option index. */
    val choices: Map<String, Int> = emptyMap(),
    val remainingSeconds: Long = LifeInUkTest.MINUTES * 60L,
    /** Answers recorded when the test was handed in; they turn from pending to marked as they sync. */
    val marked: Map<String, PracticeAnswer> = emptyMap(),
    /** True when the time ran out and the test was handed in automatically. */
    val autoSubmitted: Boolean = false,
    /** Downloaded questions, shown when there are too few for a test. */
    val available: Int = 0,
) {
    val question: Question? get() = questions.getOrNull(index)
    val total: Int get() = questions.size
    val answeredCount: Int get() = choices.size
    val unanswered: Int get() = total - answeredCount
    val correct: Int get() = marked.values.count { it.syncState == SyncState.SYNCED && it.isCorrect == true }
    /** Answers saved offline that the server has not marked yet. */
    val waiting: Int get() = marked.values.count { it.syncState == SyncState.PENDING }
    val passed: Boolean get() = correct >= LifeInUkTest.PASS_MARK
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

/**
 * A mock Life in the UK test in the official format: [LifeInUkTest.QUESTIONS] random questions,
 * [LifeInUkTest.MINUTES] minutes, no feedback until the end. Answer keys stay on the server, so
 * answers are marked through the normal practice queue when the test is handed in (offline they
 * wait and the result completes once the device is back online).
 */
class MockTestViewModel(
    private val userId: String,
    private val content: ContentRepository,
    private val practice: PracticeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val attemptId = practice.newAttemptId()
    private val state = MutableStateFlow(MockTestUiState())
    val uiState: StateFlow<MockTestUiState> = state.asStateFlow()
    private var timer: Job? = null

    init {
        viewModelScope.launch {
            val questions = content.pickSession(CefrLevel.LIFE_IN_UK, userId, LifeInUkTest.QUESTIONS)
            if (questions.size < LifeInUkTest.QUESTIONS) {
                state.update { it.copy(phase = MockPhase.NOT_ENOUGH, available = questions.size) }
            } else {
                state.update { it.copy(phase = MockPhase.RUNNING, questions = questions.shuffled()) }
                startTimer(clock() + LifeInUkTest.MINUTES * 60_000L)
            }
        }
        viewModelScope.launch {
            practice.observeAttempt(attemptId).collect { rows ->
                state.update { s -> s.copy(marked = rows.associateBy { it.questionId }) }
            }
        }
    }

    private fun startTimer(endsAt: Long) {
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

    fun select(optionIndex: Int) {
        val q = state.value.question ?: return
        if (state.value.phase != MockPhase.RUNNING) return
        state.update { it.copy(choices = it.choices + (q.id to optionIndex)) }
    }

    fun goTo(index: Int) = state.update { if (index in 0 until it.total) it.copy(index = index) else it }
    fun next() = goTo(state.value.index + 1)
    fun previous() = goTo(state.value.index - 1)

    /** Hands the test in. Unanswered questions count as wrong. */
    fun submit(auto: Boolean = false) {
        val s = state.value
        if (s.phase != MockPhase.RUNNING) return
        timer?.cancel()
        state.update { it.copy(phase = MockPhase.MARKING, autoSubmitted = auto) }
        viewModelScope.launch {
            s.questions.forEach { q ->
                val choice = s.choices[q.id] ?: return@forEach
                val answer = practice.answer(userId, attemptId, q, choice)
                state.update { it.copy(marked = it.marked + (q.id to answer)) }
            }
            state.update { it.copy(phase = MockPhase.RESULT) }
        }
    }
}
