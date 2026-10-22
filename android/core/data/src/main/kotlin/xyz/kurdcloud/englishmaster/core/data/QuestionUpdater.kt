package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.QuestionUpdateState
import xyz.kurdcloud.englishmaster.core.model.QuestionUpdateState.Result
import xyz.kurdcloud.englishmaster.core.model.isNewerThan
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

/**
 * Keeps the downloaded questions in step with the server.
 *
 * [updateAll] (the learner's one "Update all questions" button) refreshes every section that
 * changed and downloads the ones not on the device yet. [autoUpdate] runs in the background
 * when the app is online and refreshes only packs already downloaded, so newly published
 * questions reach learners without them doing anything.
 */
class QuestionUpdater(
    private val content: ContentRepository,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(QuestionUpdateState())
    val state: StateFlow<QuestionUpdateState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var lastAutoCheck: Long? = null

    fun updateAll() = start(manual = true)

    /** At most once every [AUTO_INTERVAL_MILLIS]; does nothing while offline or already running. */
    fun autoUpdate() {
        val last = lastAutoCheck
        if (last != null && clock() - last < AUTO_INTERVAL_MILLIS) return
        start(manual = false)
    }

    private fun start(manual: Boolean) {
        if (job?.isActive == true) {
            // A background run becomes visible when the learner asks for an update meanwhile.
            if (manual) mutableState.update { it.copy(manual = true) }
            return
        }
        if (!network.isOnline.value) {
            if (manual) mutableState.update { QuestionUpdateState(result = Result.OFFLINE, finishedAtMillis = clock()) }
            return
        }
        if (!manual) lastAutoCheck = clock()
        mutableState.value = QuestionUpdateState(running = true, manual = manual)
        job = scope.launch { run(manual) }
    }

    private suspend fun run(manual: Boolean) {
        val server = when (val r = content.serverVersions()) {
            is ApiResult.Success -> r.value.associateBy { it.level }
            is ApiResult.Failure -> return finish(failure(r.error), 0)
        }
        val local = content.localVersions().associateBy { it.level }
        val levels = if (manual) DOWNLOADABLE else local.keys
        val targets = levels.filter { level ->
            val remote = server[level]
            when {
                remote != null -> remote.isNewerThan(local[level])
                // Everything in a downloaded pack was retired: refresh it to an empty pack.
                else -> (local[level]?.questions ?: 0) > 0
            }
        }.sortedBy { it.ordinal }
        if (targets.isEmpty()) return finish(Result.UP_TO_DATE, 0)

        mutableState.update { it.copy(total = targets.size) }
        var questions = 0
        targets.forEachIndexed { i, level ->
            mutableState.update { it.copy(current = level, done = i) }
            when (val r = content.downloadLevel(level)) {
                is ApiResult.Success -> questions += r.value
                is ApiResult.Failure -> return finish(failure(r.error), questions)
            }
        }
        finish(Result.UPDATED, questions)
    }

    private fun failure(error: ApiError) = if (error == ApiError.Network) Result.OFFLINE else Result.FAILED

    private fun finish(result: Result, questions: Int) = mutableState.update {
        it.copy(running = false, current = null, done = it.total, result = result, updatedQuestions = questions, finishedAtMillis = clock())
    }

    companion object {
        const val AUTO_INTERVAL_MILLIS = 15 * 60 * 1000L

        /** Every section learners can download: the English levels and Life in the UK. */
        val DOWNLOADABLE: List<CefrLevel> = CefrLevel.practiceLevels + CefrLevel.LIFE_IN_UK
    }
}
