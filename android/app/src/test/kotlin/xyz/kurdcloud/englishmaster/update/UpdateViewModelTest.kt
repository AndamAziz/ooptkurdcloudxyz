package xyz.kurdcloud.englishmaster.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.kurdcloud.englishmaster.core.data.DownloadResult
import xyz.kurdcloud.englishmaster.core.data.UpdateRepository
import xyz.kurdcloud.englishmaster.core.model.AppUpdate
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val update = AppUpdate(5, "0.5.0", "https://x/app.apk", "a".repeat(64), 100, mandatory = false, "Notes", "تێبینی")
    private val apk = File("englishmaster-5.apk")

    private class FakeUpdates(var check: ApiResult<AppUpdate?>, var download: DownloadResult) : UpdateRepository {
        var gate: CompletableDeferred<Unit>? = null
        var checks = 0
        override suspend fun check(installedVersionCode: Int): ApiResult<AppUpdate?> { checks++; return check }
        override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadResult {
            onProgress(0.5f)
            gate?.await()
            onProgress(1f)
            return download
        }
    }

    private class FakeInstaller(var allowed: Boolean) : ApkInstaller {
        val installed = mutableListOf<File>()
        var settingsOpened = 0
        override val failures = MutableSharedFlow<InstallFailure>(extraBufferCapacity = 1)
        override fun canRequestInstalls() = allowed
        override fun openInstallPermissionSettings() { settingsOpened++ }
        override fun install(file: File) { installed += file }
    }

    @Test
    fun `available update downloads, verifies and goes to the system installer`() {
        val updates = FakeUpdates(ApiResult.Success(update), DownloadResult.Success(apk)).apply { gate = CompletableDeferred() }
        val installer = FakeInstaller(allowed = true)
        val vm = UpdateViewModel(updates, installer, installedVersionCode = 4, enabled = true)
        assertEquals(UpdateUiState.Available(update), vm.uiState.value)

        vm.startUpdate()
        assertEquals(UpdateUiState.Downloading(update, 0.5f), vm.uiState.value)
        updates.gate!!.complete(Unit)

        assertEquals(UpdateUiState.Installing(update), vm.uiState.value)
        assertEquals(listOf(apk), installer.installed)
    }

    @Test
    fun `without install permission the user is sent to settings and install resumes on return`() {
        val installer = FakeInstaller(allowed = false)
        val vm = UpdateViewModel(FakeUpdates(ApiResult.Success(update), DownloadResult.Success(apk)), installer, 4, true)
        vm.startUpdate()
        assertEquals(UpdateUiState.NeedsPermission(update, apk), vm.uiState.value)

        vm.openPermissionSettings()
        vm.onResume() // still not allowed
        assertTrue(installer.installed.isEmpty())

        installer.allowed = true
        vm.onResume()
        assertEquals(listOf(apk), installer.installed)
        assertEquals(1, installer.settingsOpened)
    }

    @Test
    fun `automatic check is silent when up to date or offline, manual check reports`() {
        val updates = FakeUpdates(ApiResult.Success(null), DownloadResult.Success(apk))
        val vm = UpdateViewModel(updates, FakeInstaller(true), 4, enabled = true)
        assertEquals(UpdateUiState.Hidden, vm.uiState.value)

        vm.check(manual = true)
        assertEquals(UpdateUiState.UpToDate, vm.uiState.value)

        updates.check = ApiResult.Failure(ApiError.Network)
        vm.check(manual = false)
        assertEquals(UpdateUiState.Hidden, vm.uiState.value)
        vm.check(manual = true)
        assertEquals(UpdateUiState.CheckFailed, vm.uiState.value)
    }

    @Test
    fun `mandatory update cannot be dismissed but optional one can`() {
        val vm = UpdateViewModel(FakeUpdates(ApiResult.Success(update.copy(mandatory = true)), DownloadResult.Success(apk)), FakeInstaller(true), 1, true)
        vm.dismiss()
        assertTrue(vm.uiState.value is UpdateUiState.Available)

        val optional = UpdateViewModel(FakeUpdates(ApiResult.Success(update), DownloadResult.Success(apk)), FakeInstaller(true), 4, true)
        optional.dismiss()
        assertEquals(UpdateUiState.Hidden, optional.uiState.value)
    }

    @Test
    fun `corrupt download and signature mismatch are reported`() {
        val installer = FakeInstaller(true)
        val vm = UpdateViewModel(FakeUpdates(ApiResult.Success(update), DownloadResult.Failed(DownloadResult.Reason.CORRUPT)), installer, 4, true)
        vm.startUpdate()
        assertEquals(UpdateUiState.Failed(update, UpdateUiState.Reason.CORRUPT), vm.uiState.value)
        assertTrue(installer.installed.isEmpty())

        val vm2 = UpdateViewModel(FakeUpdates(ApiResult.Success(update), DownloadResult.Success(apk)), installer, 4, true)
        vm2.startUpdate()
        installer.failures.tryEmit(InstallFailure.INCOMPATIBLE)
        assertEquals(UpdateUiState.Failed(update, UpdateUiState.Reason.INCOMPATIBLE), vm2.uiState.value)

        installer.failures.tryEmit(InstallFailure.ABORTED)
        assertEquals(UpdateUiState.Available(update), vm2.uiState.value)
    }

    @Test
    fun `disabled updater (Play builds) never checks`() {
        val updates = FakeUpdates(ApiResult.Success(update), DownloadResult.Success(apk))
        UpdateViewModel(updates, FakeInstaller(true), 4, enabled = false)
        assertEquals(0, updates.checks)
    }
}
