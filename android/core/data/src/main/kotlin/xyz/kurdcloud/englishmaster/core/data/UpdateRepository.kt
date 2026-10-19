package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.kurdcloud.englishmaster.core.model.AppUpdate
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.ReleaseApi
import xyz.kurdcloud.englishmaster.core.network.restCall
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Failed(val reason: Reason) : DownloadResult

    enum class Reason {
        /** Connection problem or HTTP error; retrying may help. */
        NETWORK,

        /** Size or SHA-256 does not match the published release; the file was deleted. */
        CORRUPT,

        /** Could not write to app storage. */
        STORAGE,
    }
}

interface UpdateRepository {
    /** The newest published release if it is newer than [installedVersionCode], else null. */
    suspend fun check(installedVersionCode: Int): ApiResult<AppUpdate?>

    /** Downloads into app-private storage and verifies size and SHA-256 before returning. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadResult
}

class SupabaseUpdateRepository(
    private val api: ReleaseApi,
    private val client: OkHttpClient,
    private val publicBucketUrl: String,
    private val directory: File,
) : UpdateRepository {

    override suspend fun check(installedVersionCode: Int): ApiResult<AppUpdate?> =
        restCall { api.latest() }.map { rows ->
            rows.firstOrNull()
                ?.takeIf { it.versionCode > installedVersionCode }
                ?.let {
                    AppUpdate(
                        versionCode = it.versionCode,
                        versionName = it.versionName,
                        downloadUrl = publicBucketUrl + it.apkPath,
                        sha256 = it.sha256.lowercase(),
                        sizeBytes = it.sizeBytes,
                        mandatory = installedVersionCode < it.minSupportedVersionCode,
                        notesEn = it.notesEn,
                        notesCkb = it.notesCkb,
                    )
                }
        }

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        if (!directory.exists() && !directory.mkdirs()) return@withContext DownloadResult.Failed(DownloadResult.Reason.STORAGE)
        // Only the APK being downloaded is kept; older downloads are removed.
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "englishmaster-${update.versionCode}.apk")

        val response = try {
            client.newCall(Request.Builder().url(update.downloadUrl).build()).execute()
        } catch (e: IOException) {
            return@withContext DownloadResult.Failed(DownloadResult.Reason.NETWORK)
        }
        response.use {
            if (!it.isSuccessful) return@withContext DownloadResult.Failed(DownloadResult.Reason.NETWORK)
            val body = it.body
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            val out = try {
                target.outputStream()
            } catch (e: IOException) {
                return@withContext DownloadResult.Failed(DownloadResult.Reason.STORAGE)
            }
            try {
                out.use {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > update.sizeBytes) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            onProgress((written.toFloat() / update.sizeBytes).coerceAtMost(1f))
                        }
                    }
                }
            } catch (e: IOException) {
                target.delete()
                return@withContext DownloadResult.Failed(DownloadResult.Reason.NETWORK)
            } catch (e: Throwable) {
                target.delete()
                throw e
            }
            val hash = digest.digest().joinToString("") { b -> "%02x".format(b) }
            if (written != update.sizeBytes || hash != update.sha256) {
                target.delete()
                return@withContext DownloadResult.Failed(DownloadResult.Reason.CORRUPT)
            }
            DownloadResult.Success(target)
        }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
