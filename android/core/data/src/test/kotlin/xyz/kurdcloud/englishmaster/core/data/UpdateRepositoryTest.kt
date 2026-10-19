package xyz.kurdcloud.englishmaster.core.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.kurdcloud.englishmaster.core.model.AppUpdate
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.network.ApiError
import xyz.kurdcloud.englishmaster.core.network.ApiResult
import xyz.kurdcloud.englishmaster.core.network.NetworkFactory
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import xyz.kurdcloud.englishmaster.core.network.SupabaseConfig
import java.security.MessageDigest

class UpdateRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var repo: SupabaseUpdateRepository
    private lateinit var bucketUrl: String

    private val apkBytes = ByteArray(300_000) { (it % 251).toByte() }
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        server.start()
        val url = server.url("/").toString().trimEnd('/').replace("http://", "https://")
        val toHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
        }.build()
        val noSession = object : SessionProvider {
            override fun current(): AuthSession? = null
            override fun update(session: AuthSession) = Unit
            override fun clear(expired: Boolean) = Unit
        }
        val services = NetworkFactory.create(SupabaseConfig(url, "anon"), noSession, toHttp)
        bucketUrl = services.publicStorageUrl("app-releases")
        repo = SupabaseUpdateRepository(services.releases, services.downloadClient, bucketUrl, folder.newFolder("updates"))
    }

    @After fun tearDown() = server.close()

    private fun release(code: Int, min: Int = 1, sha: String = apkSha, size: Long = apkBytes.size.toLong()) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(
            """[{"version_code":$code,"version_name":"0.$code","apk_path":"englishmaster-$code.apk","sha256":"$sha",
               "size_bytes":$size,"min_supported_version_code":$min,"notes_en":"Fixes","notes_ckb":"چاککردنەوە"}]""",
        ).build()

    @Test
    fun newerReleaseIsOfferedWithPublicDownloadUrl() = runTest {
        server.enqueue(release(5))
        val update = (repo.check(installedVersionCode = 4) as ApiResult.Success).value!!

        assertEquals(5, update.versionCode)
        assertEquals(bucketUrl + "englishmaster-5.apk", update.downloadUrl)
        assertTrue(update.downloadUrl.contains("/storage/v1/object/public/app-releases/"))
        assertFalse(update.mandatory)
        val query = server.takeRequest().url.toString()
        assertTrue(query.contains("published=eq.true") && query.contains("order=version_code.desc") && query.contains("limit=1"))
    }

    @Test
    fun sameOrOlderReleaseIsNotOffered() = runTest {
        server.enqueue(release(4))
        assertNull((repo.check(4) as ApiResult.Success).value)
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]").build())
        assertNull((repo.check(4) as ApiResult.Success).value)
    }

    @Test
    fun belowMinimumSupportedVersionIsMandatory() = runTest {
        server.enqueue(release(7, min = 6))
        assertTrue((repo.check(5) as ApiResult.Success).value!!.mandatory)
    }

    @Test
    fun checkFailsGracefullyOffline() = runTest {
        server.close()
        assertEquals(ApiResult.Failure(ApiError.Network), repo.check(1))
    }

    private fun update(sha: String = apkSha, size: Long = apkBytes.size.toLong()) =
        AppUpdate(9, "0.9", server.url("/apk").toString(), sha, size, false, null, null)

    @Test
    fun verifiedDownloadReportsProgressToCompletion() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(apkBytes)).build())
        val progress = mutableListOf<Float>()

        val result = repo.download(update()) { progress += it }

        val file = (result as DownloadResult.Success).file
        assertTrue(file.readBytes().contentEquals(apkBytes))
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun hashMismatchIsRejectedAndDeleted() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(apkBytes)).build())
        val result = repo.download(update(sha = "0".repeat(64))) {}
        assertEquals(DownloadResult.Failed(DownloadResult.Reason.CORRUPT), result)
        assertTrue(folder.root.resolve("updates").listFiles().isNullOrEmpty())
    }

    @Test
    fun oversizedOrTruncatedDownloadIsRejected() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(apkBytes)).build())
        assertEquals(DownloadResult.Failed(DownloadResult.Reason.CORRUPT), repo.download(update(size = 1000)) {})
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(apkBytes.copyOf(1000))).build())
        assertEquals(DownloadResult.Failed(DownloadResult.Reason.CORRUPT), repo.download(update()) {})
    }

    @Test
    fun httpErrorIsANetworkFailure() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(DownloadResult.Failed(DownloadResult.Reason.NETWORK), repo.download(update()) {})
    }
}
