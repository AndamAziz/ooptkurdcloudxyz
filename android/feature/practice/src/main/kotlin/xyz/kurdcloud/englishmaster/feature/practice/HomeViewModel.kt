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
import xyz.kurdcloud.englishmaster.core.data.NetworkMonitor
import xyz.kurdcloud.englishmaster.core.data.PracticeRepository
import xyz.kurdcloud.englishmaster.core.model.CefrLevel
import xyz.kurdcloud.englishmaster.core.model.LevelPack
import xyz.kurdcloud.englishmaster.core.model.LevelProgress
import xyz.kurdcloud.englishmaster.core.model.SyncReport.StopReason
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
)

data class HomeUiState(
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
    val levels: List<LevelRow> = CefrLevel.practiceLevels.map { LevelRow(it, null, null, null) },
    val syncing: Boolean = false,
    val message: UiMessage? = null,
)

class HomeViewModel(
    private val userId: String,
    private val content: ContentRepository,
    private val practice: PracticeRepository,
    network: NetworkMonitor,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val downloadJobs = mutableMapOf<CefrLevel, Job>()

    private data class Transient(val downloads: Map<CefrLevel, Int> = emptyMap(), val syncing: Boolean = false, val message: UiMessage? = null)

    val uiState: StateFlow<HomeUiState> = combine(
        content.observePacks(),
        practice.observeProgress(userId),
        practice.observePendingCount(userId),
        network.isOnline,
        transient,
    ) { packs, progress, pending, online, t ->
        HomeUiState(
            isOnline = online,
            pendingCount = pending,
            levels = CefrLevel.practiceLevels.map { level ->
                LevelRow(level, packs.find { it.level == level }, progress.find { it.level == level }, t.downloads[level])
            },
            syncing = t.syncing,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val online = network.isOnline

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
        }
    }

    fun messageShown() = transient.update { it.copy(message = null) }
}
