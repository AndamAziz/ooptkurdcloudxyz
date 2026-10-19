package xyz.kurdcloud.englishmaster.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.kurdcloud.englishmaster.core.data.DownloadResult
import xyz.kurdcloud.englishmaster.core.data.UpdateRepository
import xyz.kurdcloud.englishmaster.core.model.AppUpdate
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import java.io.File

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object CheckFailed : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateUiState
    data class NeedsPermission(val update: AppUpdate, val file: File) : UpdateUiState
    data class Installing(val update: AppUpdate) : UpdateUiState
    data class Failed(val update: AppUpdate, val reason: Reason) : UpdateUiState

    enum class Reason { NETWORK, CORRUPT, STORAGE, INCOMPATIBLE, INSTALL }
}

/**
 * Drives in-app updates: check → download (verified) → system installer.
 * The automatic check on start is silent unless an update exists; a manual check also
 * reports "up to date" and failures. A mandatory update cannot be dismissed.
 */
class UpdateViewModel(
    private val updates: UpdateRepository,
    private val installer: ApkInstaller,
    private val installedVersionCode: Int,
    enabled: Boolean,
) : ViewModel() {
    private val state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val uiState: StateFlow<UpdateUiState> = state.asStateFlow()
    private var download: Job? = null

    init {
        if (enabled) check(manual = false)
        viewModelScope.launch {
            installer.failures.collect { failure ->
                val update = currentUpdate() ?: return@collect
                // The user cancelling the system dialog simply returns to the offer.
                state.value = when (failure) {
                    InstallFailure.ABORTED -> UpdateUiState.Available(update)
                    InstallFailure.INCOMPATIBLE -> UpdateUiState.Failed(update, UpdateUiState.Reason.INCOMPATIBLE)
                    InstallFailure.STORAGE -> UpdateUiState.Failed(update, UpdateUiState.Reason.STORAGE)
                    InstallFailure.OTHER -> UpdateUiState.Failed(update, UpdateUiState.Reason.INSTALL)
                }
            }
        }
    }

    fun check(manual: Boolean) {
        if (state.value is UpdateUiState.Downloading || state.value is UpdateUiState.Installing) return
        if (manual) state.value = UpdateUiState.Checking
        viewModelScope.launch {
            state.value = when (val r = updates.check(installedVersionCode)) {
                is ApiResult.Success -> r.value?.let { UpdateUiState.Available(it) }
                    ?: if (manual) UpdateUiState.UpToDate else UpdateUiState.Hidden
                is ApiResult.Failure -> if (manual) UpdateUiState.CheckFailed else UpdateUiState.Hidden
            }
        }
    }

    fun startUpdate() {
        val update = currentUpdate() ?: return
        if (download?.isActive == true) return
        state.value = UpdateUiState.Downloading(update, 0f)
        download = viewModelScope.launch {
            when (val r = updates.download(update) { p -> state.value = UpdateUiState.Downloading(update, p) }) {
                is DownloadResult.Success -> install(update, r.file)
                is DownloadResult.Failed -> state.value = UpdateUiState.Failed(
                    update,
                    when (r.reason) {
                        DownloadResult.Reason.NETWORK -> UpdateUiState.Reason.NETWORK
                        DownloadResult.Reason.CORRUPT -> UpdateUiState.Reason.CORRUPT
                        DownloadResult.Reason.STORAGE -> UpdateUiState.Reason.STORAGE
                    },
                )
            }
        }
    }

    fun cancelDownload() {
        val update = currentUpdate() ?: return
        download?.cancel()
        state.value = UpdateUiState.Available(update)
    }

    fun openPermissionSettings() = installer.openInstallPermissionSettings()

    /** Called when the app returns to the foreground (e.g. from the permission screen). */
    fun onResume() {
        val s = state.value
        if (s is UpdateUiState.NeedsPermission && installer.canRequestInstalls()) install(s.update, s.file)
    }

    fun dismiss() {
        val update = currentUpdate()
        if (update != null && update.mandatory) return
        download?.cancel()
        state.value = UpdateUiState.Hidden
    }

    private fun install(update: AppUpdate, file: File) {
        if (!installer.canRequestInstalls()) {
            state.value = UpdateUiState.NeedsPermission(update, file)
            return
        }
        state.value = UpdateUiState.Installing(update)
        installer.install(file)
    }

    private fun currentUpdate(): AppUpdate? = when (val s = state.value) {
        is UpdateUiState.Available -> s.update
        is UpdateUiState.Downloading -> s.update
        is UpdateUiState.NeedsPermission -> s.update
        is UpdateUiState.Installing -> s.update
        is UpdateUiState.Failed -> s.update
        else -> null
    }
}
