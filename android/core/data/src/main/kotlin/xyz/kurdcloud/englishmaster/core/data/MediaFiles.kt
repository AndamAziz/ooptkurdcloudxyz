package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Public question media (bucket question-media). Pictures are saved next to the downloaded
 * level pack so they work offline; videos are streamed from [url].
 */
class MediaFiles(private val client: OkHttpClient, private val baseUrl: String, private val dir: File) {

    fun url(path: String): String = baseUrl + path

    /** Saves the object at [path] once; returns the local file, or null if it could not be fetched. */
    suspend fun cache(path: String): String? = withContext(Dispatchers.IO) {
        val target = File(dir, path)
        if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) return@withContext null
        if (target.isFile && target.length() > 0) return@withContext target.absolutePath
        runCatching {
            client.newCall(Request.Builder().url(url(path)).build()).execute().use { res ->
                val body = res.body
                if (!res.isSuccessful || body.contentLength() > MAX_BYTES) return@runCatching null
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (tmp.length() > MAX_BYTES || !tmp.renameTo(target)) { tmp.delete(); null } else target.absolutePath
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
