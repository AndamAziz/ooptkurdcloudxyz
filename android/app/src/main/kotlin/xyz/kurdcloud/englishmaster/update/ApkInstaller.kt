package xyz.kurdcloud.englishmaster.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

enum class InstallFailure { INCOMPATIBLE, STORAGE, ABORTED, OTHER }

/** Hands a verified APK to the system installer. Android always asks the user to confirm. */
interface ApkInstaller {
    /** Whether the user allowed this app to install apps ("Install unknown apps"). */
    fun canRequestInstalls(): Boolean
    fun openInstallPermissionSettings()
    fun install(file: File)
    val failures: SharedFlow<InstallFailure>
}

/** Results arrive asynchronously through [InstallResultReceiver]. */
internal object InstallEvents {
    val failures = MutableSharedFlow<InstallFailure>(extraBufferCapacity = 1)
}

class PackageInstallerApkInstaller(private val context: Context) : ApkInstaller {
    override val failures: SharedFlow<InstallFailure> = InstallEvents.failures.asSharedFlow()

    override fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = Intent(context, InstallResultReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, callback, flags).intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            InstallEvents.failures.tryEmit(InstallFailure.OTHER)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
            // On success the app process is replaced by the new version.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_CONFLICT ->
                InstallEvents.failures.tryEmit(InstallFailure.INCOMPATIBLE)
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallEvents.failures.tryEmit(InstallFailure.STORAGE)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallEvents.failures.tryEmit(InstallFailure.ABORTED)
            else -> InstallEvents.failures.tryEmit(InstallFailure.OTHER)
        }
    }
}
