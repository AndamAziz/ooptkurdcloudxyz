package xyz.kurdcloud.englishmaster.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.ContentRepository
import xyz.kurdcloud.englishmaster.core.data.LearningRepository
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.data.StaffRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LearnerProfile
import xyz.kurdcloud.englishmaster.core.model.LevelOverview
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.StaffAccess
import xyz.kurdcloud.englishmaster.core.model.SyncReport.StopReason
import xyz.kurdcloud.englishmaster.core.model.Topic
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult

/** [text] is a plurals resource when [plural] is true, with [arg] as the quantity. */
data class UiMessage(val text: Int, val arg: Int? = null, val plural: Boolean = false)

data class LevelRow(
    val level: CefrLevel,
    val pack: LevelPack?,
    val progress: LevelProgress?,
    /** Questions received so far while a download is running; null when idle. */
    val downloading: Int?,
    /** Server standing towards the level certificate; null offline or before it loads. */
    val overview: LevelOverview? = null,
    /** Downloaded questions whose latest marked answer was wrong. */
    val mistakes: Int = 0,
)

data class HomeUiState(
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
    val levels: List<LevelRow> = CefrLevel.practiceLevels.map { LevelRow(it, null, null, null) },
    val syncing: Boolean = false,
    val message: UiMessage? = null,
    /** Which staff screens to offer; the server enforces the real permissions. */
    val staffAccess: StaffAccess = StaffAccess.NONE,
    val profile: LearnerProfile? = null,
    val nowMillis: Long = 0,
    /** Downloaded questions per topic (empty until a level is downloaded with topic tags). */
    val topics: Map<Topic, Int> = emptyMap(),
) {
    val hasDownloads: Boolean get() = levels.any { it.pack != null }
    val certificates: Int get() = levels.count { it.overview?.certificateCode != null }
}

class HomeViewModel(
    private val userId: String,
    private val content: ContentRepository,
    private val practice: PracticeRepository,
    network: NetworkMonitor,
    private val staff: StaffRepository? = null,
    private val learning: LearningRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val downloadJobs = mutableMapOf<CefrLevel, Job>()

    private data class Transient(
        val downloads: Map<CefrLevel, Int> = emptyMap(),
        val syncing: Boolean = false,
        val message: UiMessage? = null,
        val staffAccess: StaffAccess = StaffAccess.NONE,
        val overview: Map<CefrLevel, LevelOverview> = emptyMap(),
        val profile: LearnerProfile? = null,
        val nowMillis: Long = 0,
    )

    private val local = combine(
        content.observePacks(),
        practice.observeProgress(userId),
        content.observeMistakeCounts(userId),
        content.observeTopicCounts(),
    ) { packs, progress, mistakes, topics -> LocalData(packs, progress, mistakes, topics) }

    private data class LocalData(
        val packs: List<LevelPack>,
        val progress: List<LevelProgress>,
        val mistakes: Map<CefrLevel, Int>,
        val topics: Map<Topic, Int>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        local,
        practice.observePendingCount(userId),
        network.isOnline,
        transient,
    ) { (packs, progress, mistakes, topics), pending, online, t ->
        HomeUiState(
            isOnline = online,
            pendingCount = pending,
            levels = CefrLevel.practiceLevels.map { level ->
                LevelRow(
                    level,
                    packs.find { it.level == level },
                    progress.find { it.level == level },
                    t.downloads[level],
                    t.overview[level],
                    mistakes[level] ?: 0,
                )
            },
            syncing = t.syncing,
            message = t.message,
            staffAccess = t.staffAccess,
            profile = t.profile,
            nowMillis = t.nowMillis,
            topics = topics,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val online = network.isOnline

    init {
        refreshStaffAccess()
        // Progress towards certificates comes from the server; refresh it whenever we get online.
        viewModelScope.launch { online.collect { if (it) refreshLearning() } }
    }

    /** Reloads exam/certificate standing and the learner profile; silently keeps old data on failure. */
    fun refreshLearning() {
        val repo = learning ?: return
        if (!online.value) return
        viewModelScope.launch {
            val overview = (repo.overview() as? ApiResult.Success)?.value
            val profile = (repo.profile() as? ApiResult.Success)?.value
            transient.update { t ->
                t.copy(
                    overview = overview?.associateBy { it.level } ?: t.overview,
                    profile = profile ?: t.profile,
                    nowMillis = clock(),
                )
            }
        }
    }

    /** Asks the server which roles the user has; offline or on error no staff entry is shown. */
    fun refreshStaffAccess() {
        val repo = staff ?: return
        viewModelScope.launch {
            val access = (repo.myAccess() as? ApiResult.Success)?.value ?: StaffAccess.NONE
            transient.update { it.copy(staffAccess = access) }
        }
    }

    fun download(level: CefrLevel) {
        if (downloadJobs[level]?.isActive == true) return
        if (!online.value) {
            transient.update { it.copy(message = UiMessage(R.string.practice_download_needs_internet)) }
            return
        }
        transient.update { it.copy(downloads = it.downloads + (level to 0), message = null) }
        downloadJobs[level] = viewModelScope.launch {
            try {
                val result = content.downloadLevel(level) { count ->
                    transient.update { it.copy(downloads = it.downloads + (level to count)) }
                }
                val message = when (result) {
                    is ApiResult.Success -> UiMessage(R.plurals.practice_download_done, result.value, plural = true)
                    is ApiResult.Failure -> UiMessage(
                        if (result.error == ApiError.Network) R.string.practice_download_failed_network else R.string.practice_download_failed,
                    )
                }
                transient.update { it.copy(message = message) }
            } finally {
                transient.update { it.copy(downloads = it.downloads - level) }
            }
        }
    }

    fun cancelDownload(level: CefrLevel) {
        downloadJobs.remove(level)?.cancel()
    }

    fun remove(level: CefrLevel) {
        viewModelScope.launch { content.deleteLevel(level) }
    }

    fun syncNow() {
        if (transient.value.syncing) return
        transient.update { it.copy(syncing = true, message = null) }
        viewModelScope.launch {
            val report = practice.syncNow()
            val message = when (report.stoppedReason) {
                null -> UiMessage(R.plurals.practice_sync_done, report.synced, plural = true)
                StopReason.OFFLINE -> UiMessage(R.string.practice_sync_offline)
                StopReason.SESSION_EXPIRED, StopReason.NOT_SIGNED_IN -> UiMessage(R.string.practice_sync_sign_in)
                StopReason.ACCOUNT_RESTRICTED -> UiMessage(R.string.practice_sync_restricted)
                StopReason.SERVER_UNAVAILABLE -> UiMessage(R.string.practice_sync_server)
            }
            transient.update { it.copy(syncing = false, message = message) }
            if (report.synced > 0) refreshLearning()
        }
    }

    fun messageShown() = transient.update { it.copy(message = null) }
}
